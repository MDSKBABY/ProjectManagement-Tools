package com.company.projectmanagement.workitem.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.workitem.domain.WorkItem;
import com.company.projectmanagement.workitem.domain.WorkItemReminder;
import com.company.projectmanagement.workitem.domain.WorkItemReminderStatus;
import com.company.projectmanagement.workitem.mapper.WorkItemMapper;
import com.company.projectmanagement.workitem.mapper.WorkItemReminderMapper;
import com.company.projectmanagement.workitem.web.CreateWorkItemReminderRequest;
import com.company.projectmanagement.workitem.web.WorkItemReminderResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 个人提醒业务层；所有读写均绑定当前登录用户。 */
@Service
public class WorkItemReminderService {

    private final WorkItemReminderMapper reminderMapper;
    private final WorkItemMapper workItemMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public WorkItemReminderService(
            WorkItemReminderMapper reminderMapper,
            WorkItemMapper workItemMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper) {
        this.reminderMapper = reminderMapper;
        this.workItemMapper = workItemMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    public PageResponse<WorkItemReminderResponse> list(
            Long projectId,
            int page,
            int pageSize,
            Long workItemId,
            WorkItemReminderStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        validateRange(from, to);
        if (workItemId != null) {
            requireWorkItem(projectId, workItemId);
        }
        long total = reminderMapper.count(
                projectId, actor.user().getId(), workItemId, status, from, to);
        List<WorkItemReminderResponse> data = reminderMapper.selectPage(
                        projectId,
                        actor.user().getId(),
                        workItemId,
                        status,
                        from,
                        to,
                        (page - 1) * pageSize,
                        pageSize)
                .stream()
                .map(WorkItemReminderResponse::from)
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    @Transactional
    public WorkItemReminderResponse create(
            Long projectId, CreateWorkItemReminderRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        requireWorkItem(projectId, request.workItemId());
        if (!request.remindAt().isAfter(OffsetDateTime.now())) {
            throw validationError("提醒时间必须晚于当前时间");
        }

        WorkItemReminder reminder = new WorkItemReminder();
        reminder.setProjectId(projectId);
        reminder.setWorkItemId(request.workItemId());
        reminder.setRemindAt(request.remindAt());
        reminder.setMessage(normalizeNullable(request.message()));
        reminder.setStatus(WorkItemReminderStatus.PENDING);
        reminder.setCreatedBy(actor.user().getId());
        reminderMapper.insert(reminder);
        reminderMapper.recordAudit(
                actor.user().getId(),
                "WORK_ITEM_REMINDER_CREATED",
                reminder.getId().toString(),
                projectId,
                request.workItemId());
        return WorkItemReminderResponse.from(requireOwnedReminder(
                projectId, reminder.getId(), actor.user().getId(), false));
    }

    /** 关闭操作幂等；重复请求直接返回已关闭记录且不重复写审计。 */
    @Transactional
    public WorkItemReminderResponse dismiss(
            Long projectId, Long reminderId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        WorkItemReminder reminder = requireOwnedReminder(
                projectId, reminderId, actor.user().getId(), false);
        if (reminder.getStatus() == WorkItemReminderStatus.DISMISSED) {
            return WorkItemReminderResponse.from(reminder);
        }
        if (reminderMapper.dismiss(projectId, reminderId, actor.user().getId()) > 0) {
            reminderMapper.recordAudit(
                    actor.user().getId(),
                    "WORK_ITEM_REMINDER_DISMISSED",
                    reminderId.toString(),
                    projectId,
                    reminder.getWorkItemId());
        }
        return WorkItemReminderResponse.from(requireOwnedReminder(
                projectId, reminderId, actor.user().getId(), false));
    }

    /** 删除操作幂等；软删除后不会再出现在提醒列表中。 */
    @Transactional
    public void delete(Long projectId, Long reminderId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireReadableProject(projectId, actor);
        WorkItemReminder reminder = requireOwnedReminder(
                projectId, reminderId, actor.user().getId(), true);
        if (reminder.getDeletedAt() != null) {
            return;
        }
        if (reminderMapper.softDelete(projectId, reminderId, actor.user().getId()) > 0) {
            reminderMapper.recordAudit(
                    actor.user().getId(),
                    "WORK_ITEM_REMINDER_DELETED",
                    reminderId.toString(),
                    projectId,
                    reminder.getWorkItemId());
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

    private WorkItem requireWorkItem(Long projectId, Long workItemId) {
        WorkItem item = workItemMapper.selectById(projectId, workItemId);
        if (item == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "工作项不存在");
        }
        return item;
    }

    private WorkItemReminder requireOwnedReminder(
            Long projectId, Long reminderId, Long actorUserId, boolean includeDeleted) {
        WorkItemReminder reminder = reminderMapper.selectOwnedByIdIncludingDeleted(
                projectId, reminderId, actorUserId);
        if (reminder == null || (!includeDeleted && reminder.getDeletedAt() != null)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "WORK_ITEM_REMINDER_NOT_FOUND", "工作项提醒不存在");
        }
        return reminder;
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

    private static void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw validationError("提醒结束时间不能早于开始时间");
        }
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ApiException validationError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private record Actor(AppUser user, boolean administrator) {
    }
}
