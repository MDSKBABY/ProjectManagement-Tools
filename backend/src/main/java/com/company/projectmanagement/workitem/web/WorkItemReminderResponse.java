package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemReminder;
import com.company.projectmanagement.workitem.domain.WorkItemReminderStatus;
import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import java.time.OffsetDateTime;

public record WorkItemReminderResponse(
        Long id,
        Long projectId,
        WorkItemSummary workItem,
        OffsetDateTime remindAt,
        String message,
        WorkItemReminderStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime dismissedAt) {

    public static WorkItemReminderResponse from(WorkItemReminder reminder) {
        return new WorkItemReminderResponse(
                reminder.getId(),
                reminder.getProjectId(),
                new WorkItemSummary(
                        reminder.getWorkItemId(),
                        reminder.getWorkItemTitle(),
                        reminder.getWorkItemType(),
                        reminder.getWorkItemStatus()),
                reminder.getRemindAt(),
                reminder.getMessage(),
                reminder.getStatus(),
                reminder.getCreatedAt(),
                reminder.getDismissedAt());
    }

    public record WorkItemSummary(
            Long id, String title, WorkItemType type, WorkItemStatus status) {
    }
}
