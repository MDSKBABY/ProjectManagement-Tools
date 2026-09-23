package com.company.projectmanagement.project.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 项目持久化记录及查询时附带的负责人展示信息。 */
public class Project {

    private Long id;
    private String code;
    private String name;
    private String customerName;
    private String description;
    private ProjectStatus status;
    private Long ownerId;
    private String ownerDisplayName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String tagsJson;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerDisplayName() { return ownerDisplayName; }
    public void setOwnerDisplayName(String ownerDisplayName) { this.ownerDisplayName = ownerDisplayName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
