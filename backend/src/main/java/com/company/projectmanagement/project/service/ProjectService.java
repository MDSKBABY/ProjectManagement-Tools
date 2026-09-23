package com.company.projectmanagement.project.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.domain.ProjectStatus;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.project.web.CreateProjectRequest;
import com.company.projectmanagement.project.web.ProjectResponse;
import com.company.projectmanagement.project.web.UpdateProjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 项目基础业务层，集中守住事务、资源级授权和审计一致性。 */
@Service
public class ProjectService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ObjectMapper objectMapper;

    public ProjectService(
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.objectMapper = objectMapper;
    }

    /** 列表查询在数据库层按成员关系收敛结果，管理员除外。 */
    public PageResponse<ProjectResponse> list(
            int page,
            int pageSize,
            String keyword,
            ProjectStatus status,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long totalItems = projectMapper.countVisible(
                actor.user().getId(), actor.administrator(), normalizedKeyword, status);
        List<ProjectResponse> data = projectMapper.selectVisible(
                        actor.user().getId(),
                        actor.administrator(),
                        normalizedKeyword,
                        status,
                        (page - 1) * pageSize,
                        pageSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return PageResponse.of(data, page, pageSize, totalItems);
    }

    /** 无权查看和资源不存在统一返回 404，避免泄露项目是否存在。 */
    public ProjectResponse get(Long projectId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        Project project = projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator());
        if (project == null) {
            throw projectNotFound();
        }
        return toResponse(project);
    }

    /** 创建项目、OWNER 成员关系和审计记录必须在同一事务内提交。 */
    @Transactional
    public ProjectResponse create(CreateProjectRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        validateDateRange(request.startDate(), request.endDate());
        String code = request.code().trim();
        if (projectMapper.existsActiveCode(code)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "PROJECT_CODE_EXISTS", "项目编码已存在");
        }

        Project project = new Project();
        project.setCode(code);
        project.setName(request.name().trim());
        project.setCustomerName(normalizeNullable(request.customerName()));
        project.setDescription(normalizeNullable(request.description()));
        project.setStatus(request.status() == null ? ProjectStatus.PLANNING : request.status());
        project.setOwnerId(actor.user().getId());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setTagsJson(writeTags(normalizeTags(request.tags())));
        project.setCreatedBy(actor.user().getId());
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException exception) {
            // 预检查改善提示，唯一索引仍负责阻止并发创建相同编码。
            throw new ApiException(
                    HttpStatus.CONFLICT, "PROJECT_CODE_EXISTS", "项目编码已存在");
        }
        projectMapper.insertOwnerMember(
                project.getId(), actor.user().getId(), actor.user().getId());
        projectMapper.recordAudit(
                actor.user().getId(), "PROJECT_CREATED", project.getId().toString(), code);
        return toResponse(projectMapper.selectVisibleById(
                project.getId(), actor.user().getId(), actor.administrator()));
    }

    /** 更新前同时检查功能权限之外的负责人或项目 MANAGER 身份。 */
    @Transactional
    public ProjectResponse update(
            Long projectId, UpdateProjectRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        Project project = requireMutableProject(projectId, actor);
        if (project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        validateDateRange(request.startDate(), request.endDate());

        project.setName(request.name().trim());
        project.setCustomerName(normalizeNullable(request.customerName()));
        project.setDescription(normalizeNullable(request.description()));
        project.setStatus(request.status());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setTagsJson(writeTags(normalizeTags(request.tags())));
        project.setUpdatedBy(actor.user().getId());
        if (projectMapper.update(project) == 0) {
            // 防止项目在读取和更新之间被并发软删除后仍写入成功审计。
            throw projectNotFound();
        }
        projectMapper.recordAudit(
                actor.user().getId(), "PROJECT_UPDATED", projectId.toString(), project.getCode());
        return toResponse(projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator()));
    }

    /** 软删除为幂等操作；重复请求不重复写审计。 */
    @Transactional
    public void delete(Long projectId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        Project project = requireMutableProject(projectId, actor);
        if (project.getDeletedAt() != null) {
            return;
        }
        if (projectMapper.softDelete(projectId, actor.user().getId()) == 0) {
            // 并发删除视为幂等成功，且不能重复记录删除审计。
            return;
        }
        projectMapper.recordAudit(
                actor.user().getId(), "PROJECT_DELETED", projectId.toString(), project.getCode());
    }

    private Project requireMutableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null) {
            throw projectNotFound();
        }
        if (!actor.administrator()
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权管理该项目");
        }
        return project;
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

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getCustomerName(),
                project.getDescription(),
                project.getStatus(),
                new ProjectResponse.OwnerResponse(
                        project.getOwnerId(), project.getOwnerDisplayName()),
                project.getStartDate(),
                project.getEndDate(),
                readTags(project.getTagsJson()),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private List<String> normalizeTags(List<String> tags) {
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
            throw new IllegalStateException("无法序列化项目标签", exception);
        }
    }

    private List<String> readTags(String tagsJson) {
        try {
            return objectMapper.readValue(tagsJson == null ? "[]" : tagsJson, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的项目标签格式不正确", exception);
        }
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PROJECT_DATE_RANGE",
                    "项目结束日期不能早于开始日期");
        }
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private record Actor(AppUser user, boolean administrator) {
    }
}
