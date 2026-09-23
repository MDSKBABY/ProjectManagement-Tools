package com.company.projectmanagement.environment.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.environment.domain.EnvironmentFingerprint;
import com.company.projectmanagement.environment.mapper.EnvironmentFingerprintMapper;
import com.company.projectmanagement.environment.web.EnvironmentFingerprintResponse;
import com.company.projectmanagement.environment.web.SaveEnvironmentFingerprintRequest;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 管理环境指纹的规范化字段、项目授权和审计。 */
@Service
public class EnvironmentFingerprintService {

    private final EnvironmentFingerprintMapper mapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ObjectMapper objectMapper;

    public EnvironmentFingerprintService(
            EnvironmentFingerprintMapper mapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.objectMapper = objectMapper;
    }

    public PageResponse<EnvironmentFingerprintResponse> list(
            Long projectId, int page, int pageSize, String keyword,
            DeploymentEnvironment environment, String operatingSystem, String architecture,
            String databaseName, String middleware, String tag, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        String normalizedKeyword = normalizeNullable(keyword);
        String normalizedOperatingSystem = normalizeNullable(operatingSystem);
        String normalizedArchitecture = normalizeNullable(architecture);
        String normalizedDatabaseName = normalizeNullable(databaseName);
        String normalizedMiddleware = normalizeNullable(middleware);
        String normalizedTag = normalizeNullable(tag);
        long total = mapper.count(projectId, normalizedKeyword, environment,
                normalizedOperatingSystem, normalizedArchitecture, normalizedDatabaseName,
                normalizedMiddleware, normalizedTag);
        List<EnvironmentFingerprintResponse> data = mapper.selectPage(
                        projectId, normalizedKeyword, environment, normalizedOperatingSystem,
                        normalizedArchitecture, normalizedDatabaseName, normalizedMiddleware,
                        normalizedTag, (page - 1) * pageSize, pageSize)
                .stream().map(this::response).toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    public EnvironmentFingerprintResponse detail(
            Long projectId, Long fingerprintId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        return response(requireFingerprint(projectId, fingerprintId));
    }

    @Transactional
    public EnvironmentFingerprintResponse create(
            Long projectId, SaveEnvironmentFingerprintRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        EnvironmentFingerprint fingerprint = map(request, new EnvironmentFingerprint());
        fingerprint.setProjectId(projectId);
        fingerprint.setCreatedBy(actor.user().getId());
        fingerprint.setUpdatedBy(actor.user().getId());
        try {
            mapper.insert(fingerprint);
        } catch (DuplicateKeyException exception) {
            throw duplicateName();
        }
        mapper.recordAudit(actor.user().getId(), "ENVIRONMENT_FINGERPRINT_CREATED",
                fingerprint.getId().toString(), projectId);
        return response(mapper.selectById(projectId, fingerprint.getId()));
    }

    @Transactional
    public EnvironmentFingerprintResponse update(
            Long projectId, Long fingerprintId, SaveEnvironmentFingerprintRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        requireFingerprint(projectId, fingerprintId);
        EnvironmentFingerprint fingerprint = map(request, new EnvironmentFingerprint());
        fingerprint.setId(fingerprintId);
        fingerprint.setProjectId(projectId);
        fingerprint.setUpdatedBy(actor.user().getId());
        try {
            mapper.update(fingerprint);
        } catch (DuplicateKeyException exception) {
            throw duplicateName();
        }
        mapper.recordAudit(actor.user().getId(), "ENVIRONMENT_FINGERPRINT_UPDATED",
                fingerprintId.toString(), projectId);
        return response(mapper.selectById(projectId, fingerprintId));
    }

    @Transactional
    public void delete(Long projectId, Long fingerprintId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        requireFingerprint(projectId, fingerprintId);
        if (mapper.countActiveSolutions(fingerprintId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ENVIRONMENT_FINGERPRINT_IN_USE",
                    "环境指纹已被部署方案使用，不能删除");
        }
        mapper.softDelete(projectId, fingerprintId, actor.user().getId());
        mapper.recordAudit(actor.user().getId(), "ENVIRONMENT_FINGERPRINT_DELETED",
                fingerprintId.toString(), projectId);
    }

    private EnvironmentFingerprint map(
            SaveEnvironmentFingerprintRequest request, EnvironmentFingerprint fingerprint) {
        fingerprint.setName(request.name().trim());
        fingerprint.setEnvironment(request.environment());
        fingerprint.setOperatingSystem(request.operatingSystem().trim());
        fingerprint.setOsVersion(normalizeNullable(request.osVersion()));
        fingerprint.setKernelVersion(normalizeNullable(request.kernelVersion()));
        fingerprint.setArchitecture(request.architecture().trim());
        fingerprint.setRuntimeName(normalizeNullable(request.runtimeName()));
        fingerprint.setRuntimeVersion(normalizeNullable(request.runtimeVersion()));
        fingerprint.setDatabaseName(normalizeNullable(request.databaseName()));
        fingerprint.setDatabaseVersion(normalizeNullable(request.databaseVersion()));
        fingerprint.setMiddlewaresJson(writeList(normalizeList(request.middlewares())));
        fingerprint.setNetworkZone(normalizeNullable(request.networkZone()));
        fingerprint.setTagsJson(writeList(normalizeList(request.tags())));
        fingerprint.setNotes(normalizeNullable(request.notes()));
        return fingerprint;
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化环境指纹列表字段", exception);
        }
    }

    private EnvironmentFingerprintResponse response(EnvironmentFingerprint fingerprint) {
        return EnvironmentFingerprintResponse.from(fingerprint, objectMapper);
    }

    private EnvironmentFingerprint requireFingerprint(Long projectId, Long fingerprintId) {
        EnvironmentFingerprint fingerprint = mapper.selectById(projectId, fingerprintId);
        if (fingerprint == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_FINGERPRINT_NOT_FOUND",
                    "环境指纹不存在");
        }
        return fingerprint;
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(projectId, actor.user().getId(), actor.administrator()) == null) {
            throw projectNotFound();
        }
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) throw projectNotFound();
        if (!actor.administrator() && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED",
                    "无权维护该项目的环境指纹");
        }
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        boolean administrator = identityAccessMapper.selectRoleCodesByUserId(user.getId()).contains("ADMIN");
        return new Actor(user, administrator);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return List.copyOf(values.stream().map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException duplicateName() {
        return new ApiException(HttpStatus.CONFLICT, "ENVIRONMENT_FINGERPRINT_NAME_CONFLICT",
                "项目内已存在同名环境指纹");
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private record Actor(AppUser user, boolean administrator) { }
}
