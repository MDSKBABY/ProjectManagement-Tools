package com.company.projectmanagement.workitem.domain;

import java.time.OffsetDateTime;

/** 状态变更日志为只追加记录，不随工作项后续编辑而覆盖。 */
public class WorkItemStatusLog {
    private Long id;
    private Long workItemId;
    private WorkItemStatus fromStatus;
    private WorkItemStatus toStatus;
    private String comment;
    private Long changedBy;
    private String changedByDisplayName;
    private OffsetDateTime changedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkItemId() { return workItemId; }
    public void setWorkItemId(Long workItemId) { this.workItemId = workItemId; }
    public WorkItemStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(WorkItemStatus value) { this.fromStatus = value; }
    public WorkItemStatus getToStatus() { return toStatus; }
    public void setToStatus(WorkItemStatus value) { this.toStatus = value; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long value) { this.changedBy = value; }
    public String getChangedByDisplayName() { return changedByDisplayName; }
    public void setChangedByDisplayName(String value) { this.changedByDisplayName = value; }
    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(OffsetDateTime value) { this.changedAt = value; }
}
