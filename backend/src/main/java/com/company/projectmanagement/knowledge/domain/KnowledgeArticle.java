package com.company.projectmanagement.knowledge.domain;

import java.time.OffsetDateTime;

/** 项目内技术知识文章；正文和审核信息共同形成可追溯的发布状态。 */
public class KnowledgeArticle {
    private Long id;
    private Long projectId;
    private String title;
    private String scenario;
    private String symptom;
    private String cause;
    private String solution;
    private String applicableConditions;
    private String tagsJson;
    private KnowledgeArticleStatus status;
    private String reviewComment;
    private Long reviewedBy;
    private String reviewedByDisplayName;
    private OffsetDateTime reviewedAt;
    private Long createdBy;
    private String createdByDisplayName;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
    private Integer attachmentCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getSymptom() { return symptom; }
    public void setSymptom(String symptom) { this.symptom = symptom; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }
    public String getApplicableConditions() { return applicableConditions; }
    public void setApplicableConditions(String applicableConditions) { this.applicableConditions = applicableConditions; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public KnowledgeArticleStatus getStatus() { return status; }
    public void setStatus(KnowledgeArticleStatus status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedByDisplayName() { return reviewedByDisplayName; }
    public void setReviewedByDisplayName(String reviewedByDisplayName) { this.reviewedByDisplayName = reviewedByDisplayName; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByDisplayName() { return createdByDisplayName; }
    public void setCreatedByDisplayName(String createdByDisplayName) { this.createdByDisplayName = createdByDisplayName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Integer getAttachmentCount() { return attachmentCount; }
    public void setAttachmentCount(Integer attachmentCount) { this.attachmentCount = attachmentCount; }
}
