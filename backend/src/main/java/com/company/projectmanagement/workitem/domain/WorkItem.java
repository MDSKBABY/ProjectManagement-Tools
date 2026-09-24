package com.company.projectmanagement.workitem.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 统一工作项持久化模型，查询时附带负责人展示名称。 */
public class WorkItem {
    private Long id;
    private Long projectId;
    private WorkItemType type;
    private String title;
    private String description;
    private WorkItemStatus status;
    private WorkItemPriority priority;
    private Long assigneeId;
    private String assigneeDisplayName;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public WorkItemType getType() { return type; }
    public void setType(WorkItemType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public WorkItemStatus getStatus() { return status; }
    public void setStatus(WorkItemStatus status) { this.status = status; }
    public WorkItemPriority getPriority() { return priority; }
    public void setPriority(WorkItemPriority priority) { this.priority = priority; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getAssigneeDisplayName() { return assigneeDisplayName; }
    public void setAssigneeDisplayName(String value) { this.assigneeDisplayName = value; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public void setPlannedStartDate(LocalDate value) { this.plannedStartDate = value; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDate value) { this.plannedEndDate = value; }
    public LocalDate getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(LocalDate value) { this.actualStartDate = value; }
    public LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDate value) { this.actualEndDate = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt = value; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime value) { this.deletedAt = value; }
}
