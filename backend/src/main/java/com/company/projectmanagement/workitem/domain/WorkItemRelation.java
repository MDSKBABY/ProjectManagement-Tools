package com.company.projectmanagement.workitem.domain;

import java.time.OffsetDateTime;

/** 工作项有向关系查询模型，附带两端工作项摘要。 */
public class WorkItemRelation {
    private Long id;
    private Long projectId;
    private Long sourceWorkItemId;
    private String sourceTitle;
    private WorkItemType sourceType;
    private WorkItemStatus sourceStatus;
    private Long targetWorkItemId;
    private String targetTitle;
    private WorkItemType targetType;
    private WorkItemStatus targetStatus;
    private WorkItemRelationType type;
    private Long createdBy;
    private String createdByDisplayName;
    private OffsetDateTime createdAt;
    private Long deletedBy;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public Long getSourceWorkItemId() { return sourceWorkItemId; }
    public void setSourceWorkItemId(Long value) { sourceWorkItemId = value; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String value) { sourceTitle = value; }
    public WorkItemType getSourceType() { return sourceType; }
    public void setSourceType(WorkItemType value) { sourceType = value; }
    public WorkItemStatus getSourceStatus() { return sourceStatus; }
    public void setSourceStatus(WorkItemStatus value) { sourceStatus = value; }
    public Long getTargetWorkItemId() { return targetWorkItemId; }
    public void setTargetWorkItemId(Long value) { targetWorkItemId = value; }
    public String getTargetTitle() { return targetTitle; }
    public void setTargetTitle(String value) { targetTitle = value; }
    public WorkItemType getTargetType() { return targetType; }
    public void setTargetType(WorkItemType value) { targetType = value; }
    public WorkItemStatus getTargetStatus() { return targetStatus; }
    public void setTargetStatus(WorkItemStatus value) { targetStatus = value; }
    public WorkItemRelationType getType() { return type; }
    public void setType(WorkItemRelationType value) { type = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public String getCreatedByDisplayName() { return createdByDisplayName; }
    public void setCreatedByDisplayName(String value) { createdByDisplayName = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long value) { deletedBy = value; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime value) { deletedAt = value; }
}
