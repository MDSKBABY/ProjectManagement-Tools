package com.company.projectmanagement.deployment.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.domain.DeploymentAsset;
import com.company.projectmanagement.deployment.domain.DeploymentAssetType;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.deployment.domain.RiskLevel;
import com.company.projectmanagement.deployment.mapper.DeploymentAssetMapper;
import com.company.projectmanagement.deployment.web.CreateDeploymentAssetRequest;
import com.company.projectmanagement.deployment.web.DeploymentAssetResponse;
import com.company.projectmanagement.file.domain.FileAsset;
import com.company.projectmanagement.file.domain.FileStatus;
import com.company.projectmanagement.file.mapper.FileAssetMapper;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 部署资产服务，负责不可变版本、项目边界、文件可用性和脚本安全说明。 */
@Service
public class DeploymentAssetService {

    private final DeploymentAssetMapper deploymentAssetMapper;
    private final FileAssetMapper fileAssetMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ObjectMapper objectMapper;

    public DeploymentAssetService(
            DeploymentAssetMapper deploymentAssetMapper,
            FileAssetMapper fileAssetMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            ObjectMapper objectMapper) {
        this.deploymentAssetMapper = deploymentAssetMapper;
        this.fileAssetMapper = fileAssetMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<DeploymentAssetResponse> list(
            Long projectId,
            int page,
            int pageSize,
            String keyword,
            DeploymentAssetType assetType,
            String operatingSystem,
            String architecture,
            DeploymentEnvironment environment,
            RiskLevel riskLevel,
            String tag,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        String normalizedKeyword = normalizeNullable(keyword);
        String normalizedOperatingSystem = normalizeNullable(operatingSystem);
        String normalizedArchitecture = normalizeNullable(architecture);
        String normalizedTag = normalizeNullable(tag);
        long total = deploymentAssetMapper.count(
                projectId,
                normalizedKeyword,
                assetType,
                normalizedOperatingSystem,
                normalizedArchitecture,
                environment,
                riskLevel,
                normalizedTag);
        List<DeploymentAssetResponse> data = deploymentAssetMapper.selectPage(
                        projectId,
                        normalizedKeyword,
                        assetType,
                        normalizedOperatingSystem,
                        normalizedArchitecture,
                        environment,
                        riskLevel,
                        normalizedTag,
                        (page - 1) * pageSize,
                        pageSize)
                .stream()
                .map(this::response)
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    public DeploymentAssetResponse detail(
            Long projectId, Long assetId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        DeploymentAsset asset = deploymentAssetMapper.selectById(projectId, assetId);
        if (asset == null) {
            throw assetNotFound();
        }
        return response(asset);
    }

    /** 每次创建都写入新记录，既有资产版本没有更新接口。 */
    @Transactional
    public DeploymentAssetResponse create(
            Long projectId, CreateDeploymentAssetRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        validateScriptInstructions(request);

        FileAsset file = fileAssetMapper.selectProjectFile(projectId, request.fileAssetId());
        if (file == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在");
        }
        if (file.getStatus() != FileStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_NOT_AVAILABLE", "文件尚不可用于部署资产");
        }

        VersionTarget target = resolveVersion(projectId, request.assetGroupId());
        DeploymentAsset asset = new DeploymentAsset();
        asset.setAssetGroupId(target.assetGroupId());
        asset.setVersion(target.version());
        asset.setProjectId(projectId);
        asset.setName(request.name().trim());
        asset.setAssetType(request.assetType());
        asset.setVersionLabel(request.versionLabel().trim());
        asset.setFileAssetId(request.fileAssetId());
        asset.setOperatingSystem(normalizeNullable(request.operatingSystem()));
        asset.setArchitecture(normalizeNullable(request.architecture()));
        asset.setEnvironment(request.environment());
        asset.setRiskLevel(request.riskLevel());
        asset.setTagsJson(writeTags(normalizeTags(request.tags())));
        asset.setDescription(normalizeNullable(request.description()));
        asset.setPrerequisites(normalizeNullable(request.prerequisites()));
        asset.setExecutionInstructions(normalizeNullable(request.executionInstructions()));
        asset.setRollbackInstructions(normalizeNullable(request.rollbackInstructions()));
        asset.setCreatedBy(actor.user().getId());

        try {
            deploymentAssetMapper.insert(asset);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DEPLOYMENT_ASSET_VERSION_CONFLICT",
                    "该部署资产版本已被其他操作创建，请刷新后重试");
        }
        deploymentAssetMapper.linkFile(
                file.getId(), projectId, asset.getId(), actor.user().getId());
        deploymentAssetMapper.recordCreatedAudit(
                actor.user().getId(), asset.getId().toString(), projectId, asset.getVersion());
        return response(deploymentAssetMapper.selectById(projectId, asset.getId()));
    }

    public List<DeploymentAssetResponse> versions(
            Long projectId, String assetGroupId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        validateGroupId(assetGroupId);
        List<DeploymentAssetResponse> versions = deploymentAssetMapper
                .selectVersions(projectId, assetGroupId)
                .stream()
                .map(this::response)
                .toList();
        if (versions.isEmpty()) {
            throw assetNotFound();
        }
        return versions;
    }

    private VersionTarget resolveVersion(Long projectId, String requestedGroupId) {
        if (!StringUtils.hasText(requestedGroupId)) {
            return new VersionTarget(UUID.randomUUID().toString(), 1);
        }
        String groupId = requestedGroupId.trim();
        validateGroupId(groupId);
        DeploymentAsset latest = deploymentAssetMapper.selectLatestVersion(projectId, groupId);
        if (latest == null) {
            throw assetNotFound();
        }
        return new VersionTarget(groupId, latest.getVersion() + 1);
    }

    private static void validateScriptInstructions(CreateDeploymentAssetRequest request) {
        if (request.assetType() != DeploymentAssetType.SCRIPT) {
            return;
        }
        if (!StringUtils.hasText(request.prerequisites())
                || !StringUtils.hasText(request.executionInstructions())
                || !StringUtils.hasText(request.rollbackInstructions())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SCRIPT_INSTRUCTIONS_REQUIRED",
                    "脚本必须填写前置条件、执行方式和回滚说明");
        }
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return List.copyOf(tags.stream()
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化部署资产标签", exception);
        }
    }

    private DeploymentAssetResponse response(DeploymentAsset asset) {
        return DeploymentAssetResponse.from(asset, objectMapper);
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator()) == null) {
            throw projectNotFound();
        }
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        if (!actor.administrator()
                && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权维护该项目的部署资产");
        }
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        boolean administrator = identityAccessMapper.selectRoleCodesByUserId(user.getId())
                .contains("ADMIN");
        return new Actor(user, administrator);
    }

    private static void validateGroupId(String assetGroupId) {
        try {
            UUID.fromString(assetGroupId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DEPLOYMENT_ASSET_GROUP_ID_INVALID",
                    "部署资产版本组标识格式不正确");
        }
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private static ApiException assetNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_ASSET_NOT_FOUND", "部署资产不存在");
    }

    private record VersionTarget(String assetGroupId, int version) { }
    private record Actor(AppUser user, boolean administrator) { }
}
