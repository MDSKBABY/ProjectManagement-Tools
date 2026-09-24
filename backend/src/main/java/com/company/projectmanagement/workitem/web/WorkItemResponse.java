package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItem;
import com.company.projectmanagement.workitem.domain.WorkItemPriority;
import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 工作项对外响应仅包含负责人的非敏感展示信息。 */
public record WorkItemResponse(
        Long id,
        Long projectId,
        WorkItemType type,
        String title,
        String description,
        WorkItemStatus status,
        WorkItemPriority priority,
        AssigneeResponse assignee,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static WorkItemResponse from(WorkItem item) {
        AssigneeResponse assignee = item.getAssigneeId() == null
                ? null
                : new AssigneeResponse(item.getAssigneeId(), item.getAssigneeDisplayName());
        return new WorkItemResponse(
                item.getId(), item.getProjectId(), item.getType(), item.getTitle(),
                item.getDescription(), item.getStatus(), item.getPriority(), assignee,
                item.getPlannedStartDate(), item.getPlannedEndDate(),
                item.getActualStartDate(), item.getActualEndDate(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    public record AssigneeResponse(Long id, String displayName) {
    }
}
