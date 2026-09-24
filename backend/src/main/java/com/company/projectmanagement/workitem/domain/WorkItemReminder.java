package com.company.projectmanagement.workitem.domain;

import java.time.OffsetDateTime;

/** 工作项个人提醒查询模型，包含前端展示所需的工作项摘要。 */
public class WorkItemReminder {
    private Long id;
    private Long projectId;
    private Long workItemId;
    private String workItemTitle;
    private WorkItemType workItemType;
    private WorkItemStatus workItemStatus;
    private OffsetDateTime remindAt;
    private String message;
    private WorkItemReminderStatus status;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime dismissedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getWorkItemId() { return workItemId; }
    public void setWorkItemId(Long value) { workItemId = value; }
    public String getWorkItemTitle() { return workItemTitle; }
    public void setWorkItemTitle(String value) { workItemTitle = value; }
    public WorkItemType getWorkItemType() { return workItemType; }
    public void setWorkItemType(WorkItemType value) { workItemType = value; }
    public WorkItemStatus getWorkItemStatus() { return workItemStatus; }
    public void setWorkItemStatus(WorkItemStatus value) { workItemStatus = value; }
    public OffsetDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(OffsetDateTime value) { remindAt = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public WorkItemReminderStatus getStatus() { return status; }
    public void setStatus(WorkItemReminderStatus value) { status = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public OffsetDateTime getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(OffsetDateTime value) { dismissedAt = value; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime value) { deletedAt = value; }
}
