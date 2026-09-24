package com.company.projectmanagement.workitem.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.domain.ProjectMember;
import com.company.projectmanagement.project.domain.ProjectMemberRole;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.project.mapper.ProjectMemberMapper;
import com.company.projectmanagement.workitem.domain.WorkItem;
import com.company.projectmanagement.workitem.domain.WorkItemPriority;
import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import com.company.projectmanagement.workitem.mapper.WorkItemMapper;
import com.company.projectmanagement.workitem.web.CreateWorkItemRequest;
import com.company.projectmanagement.workitem.web.TransitionWorkItemStatusRequest;
import com.company.projectmanagement.workitem.web.UpdateWorkItemRequest;
import com.company.projectmanagement.workitem.web.WorkItemResponse;
import com.company.projectmanagement.workitem.web.WorkItemStatusLogResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 统一工作项业务层，集中处理项目授权、状态机、历史与审计一致性。 */
@Service
public class WorkItemService {

    private final WorkItemMapper workItemMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public WorkItemService(
            WorkItemMapper workItemMapper,
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper) {
        this.workItemMapper = workItemMapper;
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    /** 列表查询先确认项目可见，再在数据库层分页和筛选。 */
    public PageResponse<WorkItemResponse> list(
            Long projectId,
            int page,
            int pageSize,
            String keyword,
            WorkItemType type,
            WorkItemStatus status,
            WorkItemPriority priority,
            Long assigneeId,
            LocalDate plannedFrom,
            LocalDate plannedTo,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        validateDateRange(plannedFrom, plannedTo);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = workItemMapper.count(
                projectId, normalizedKeyword, type, status, priority,
                assigneeId, plannedFrom, plannedTo);
        List<WorkItemResponse> data = workItemMapper.selectPage(
                        projectId, normalizedKeyword, type, status, priority,
                        assigneeId, plannedFrom, plannedTo,
                        (page - 1) * pageSize, pageSize)
                .stream()
                .map(WorkItemResponse::from)
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    /** 详情查询对越权项目和不存在工作项统一返回 404。 */
    public WorkItemResponse get(Long projectId, Long workItemId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        return WorkItemResponse.from(requireWorkItem(projectId, workItemId));
    }

    /** 创建、初始状态历史与审计记录必须在同一事务中提交。 */
    @Transactional
    public WorkItemResponse create(
            Long projectId, CreateWorkItemRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireManageableProject(projectId, actor);
        validateDateRange(request.plannedStartDate(), request.plannedEndDate());
        validateAssignee(projectId, request.assigneeId());

        WorkItem item = new WorkItem();
        item.setProjectId(projectId);
        item.setType(request.type());
        item.setTitle(request.title().trim());
        item.setDescription(normalizeNullable(request.description()));
        item.setStatus(WorkItemStatus.TODO);
        item.setPriority(request.priority() == null ? WorkItemPriority.NORMAL : request.priority());
        item.setAssigneeId(request.assigneeId());
        item.setPlannedStartDate(request.plannedStartDate());
        item.setPlannedEndDate(request.plannedEndDate());
        item.setCreatedBy(actor.user().getId());
        workItemMapper.insert(item);
        workItemMapper.insertStatusLog(
                item.getId(), null, WorkItemStatus.TODO, null, actor.user().getId());
        workItemMapper.recordAudit(
                actor.user().getId(), "WORK_ITEM_CREATED",
                item.getId().toString(), projectId);
        return WorkItemResponse.from(requireWorkItem(projectId, item.getId()));
    }

    /** PATCH 只替换请求中显式出现的字段，显式 null 可清空可选字段。 */
    @Transactional
    public WorkItemResponse update(
            Long projectId,
            Long workItemId,
            UpdateWorkItemRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        WorkItem item = requireWorkItem(projectId, workItemId);
        boolean manager = canManageProject(projectId, actor);
        if (!manager && !actor.user().getId().equals(item.getAssigneeId())) {
            throw accessDenied();
        }
        if (!request.hasChanges()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请至少提供一个要修改的字段");
        }
        if (!manager && (request.isTypePresent() || request.isAssigneeIdPresent())) {
            throw accessDenied();
        }
        applyPatch(item, request, projectId);
        validateDateRange(item.getPlannedStartDate(), item.getPlannedEndDate());
        item.setUpdatedBy(actor.user().getId());
        if (workItemMapper.updateMetadata(item) == 0) {
            throw workItemNotFound();
        }
        workItemMapper.recordAudit(
                actor.user().getId(), "WORK_ITEM_UPDATED", workItemId.toString(), projectId);
        return WorkItemResponse.from(requireWorkItem(projectId, workItemId));
    }

    /** 软删除为幂等操作，只有首次删除会写入审计。 */
    @Transactional
    public void delete(Long projectId, Long workItemId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireManageableProject(projectId, actor);
        WorkItem item = workItemMapper.selectByIdIncludingDeleted(projectId, workItemId);
        if (item == null) {
            throw workItemNotFound();
        }
        if (item.getDeletedAt() != null) {
            return;
        }
        if (workItemMapper.softDelete(projectId, workItemId, actor.user().getId()) == 0) {
            return;
        }
        workItemMapper.recordAudit(
                actor.user().getId(), "WORK_ITEM_DELETED", workItemId.toString(), projectId);
    }

    /** 状态变更单独校验允许的边，并以乐观条件防止并发覆盖。 */
    @Transactional
    public WorkItemResponse transition(
            Long projectId,
            Long workItemId,
            TransitionWorkItemStatusRequest request,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        WorkItem item = requireWorkItem(projectId, workItemId);
        if (!canManageProject(projectId, actor)
                && !actor.user().getId().equals(item.getAssigneeId())) {
            throw accessDenied();
        }
        WorkItemStatus from = item.getStatus();
        WorkItemStatus to = request.status();
        if (!allowedTargets(from).contains(to)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WORK_ITEM_STATUS_TRANSITION_INVALID",
                    "当前状态不允许流转到目标状态");
        }

        LocalDate actualStart = item.getActualStartDate();
        LocalDate actualEnd = item.getActualEndDate();
        LocalDate today = LocalDate.now();
        if ((to == WorkItemStatus.IN_PROGRESS || to == WorkItemStatus.DONE)
                && actualStart == null) {
            actualStart = today;
        }
        if (to == WorkItemStatus.DONE) {
            actualEnd = today;
        } else if (from == WorkItemStatus.DONE) {
            actualEnd = null;
        }
        if (workItemMapper.updateStatus(
                projectId, workItemId, from, to, actualStart, actualEnd,
                actor.user().getId()) == 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WORK_ITEM_STATUS_CHANGED",
                    "工作项状态已被其他请求修改，请刷新后重试");
        }
        workItemMapper.insertStatusLog(
                workItemId, from, to, normalizeNullable(request.comment()), actor.user().getId());
        workItemMapper.recordAudit(
                actor.user().getId(), "WORK_ITEM_STATUS_CHANGED",
                workItemId.toString(), projectId);
        return WorkItemResponse.from(requireWorkItem(projectId, workItemId));
    }

    /** 状态历史按发生顺序返回，便于前端直接呈现时间线。 */
    public List<WorkItemStatusLogResponse> statusHistory(
            Long projectId, Long workItemId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        WorkItem item = requireWorkItem(projectId, workItemId);
        return workItemMapper.selectStatusHistory(item.getId()).stream()
                .map(WorkItemStatusLogResponse::from)
                .toList();
    }

    private void applyPatch(WorkItem item, UpdateWorkItemRequest request, Long projectId) {
        if (request.isTypePresent()) {
            if (request.getType() == null) {
                throw validationError("工作项类型不能为空");
            }
            item.setType(request.getType());
        }
        if (request.isTitlePresent()) {
            if (!StringUtils.hasText(request.getTitle()) || request.getTitle().trim().length() > 200) {
                throw validationError("工作项标题长度必须为 1‑200 个字符");
            }
            item.setTitle(request.getTitle().trim());
        }
        if (request.isDescriptionPresent()) {
            item.setDescription(normalizeNullable(request.getDescription()));
        }
        if (request.isPriorityPresent()) {
            if (request.getPriority() == null) {
                throw validationError("优先级不能为空");
            }
            item.setPriority(request.getPriority());
        }
        if (request.isAssigneeIdPresent()) {
            if (request.getAssigneeId() != null && request.getAssigneeId() <= 0) {
                throw validationError("负责人标识不正确");
            }
            validateAssignee(projectId, request.getAssigneeId());
            item.setAssigneeId(request.getAssigneeId());
        }
        if (request.isPlannedStartDatePresent()) {
            item.setPlannedStartDate(request.getPlannedStartDate());
        }
        if (request.isPlannedEndDatePresent()) {
            item.setPlannedEndDate(request.getPlannedEndDate());
        }
    }

    private void validateAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return;
        }
        ProjectMember member = projectMemberMapper.selectOne(projectId, assigneeId);
        if (member == null
                || member.getUserStatus() != UserStatus.ACTIVE
                || member.getProjectRole() == ProjectMemberRole.VIEWER) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WORK_ITEM_ASSIGNEE_INVALID",
                    "负责人必须是当前项目的有效成员");
        }
    }

    private Project requireReadableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator());
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
        return project;
    }

    private void requireManageableProject(Long projectId, Actor actor) {
        requireReadableProject(projectId, actor);
        if (!canManageProject(projectId, actor)) {
            throw accessDenied();
        }
    }

    private boolean canManageProject(Long projectId, Actor actor) {
        return actor.administrator()
                || projectMapper.canManage(projectId, actor.user().getId());
    }

    private WorkItem requireWorkItem(Long projectId, Long workItemId) {
        WorkItem item = workItemMapper.selectById(projectId, workItemId);
        if (item == null) {
            throw workItemNotFound();
        }
        return item;
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

    private static Set<WorkItemStatus> allowedTargets(WorkItemStatus from) {
        return switch (from) {
            case TODO -> Set.of(
                    WorkItemStatus.IN_PROGRESS, WorkItemStatus.DONE, WorkItemStatus.CANCELED);
            case IN_PROGRESS -> Set.of(
                    WorkItemStatus.TODO, WorkItemStatus.DONE, WorkItemStatus.CANCELED);
            case DONE -> Set.of(WorkItemStatus.IN_PROGRESS);
            case CANCELED -> Set.of(WorkItemStatus.TODO);
        };
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WORK_ITEM_DATE_RANGE",
                    "工作项计划结束日期不能早于开始日期");
        }
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException validationError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ApiException accessDenied() {
        return new ApiException(
                HttpStatus.FORBIDDEN, "WORK_ITEM_ACCESS_DENIED", "无权维护该工作项");
    }

    private static ApiException workItemNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "工作项不存在");
    }

    private record Actor(AppUser user, boolean administrator) {
    }
}
