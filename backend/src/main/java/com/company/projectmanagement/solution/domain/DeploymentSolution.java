package com.company.projectmanagement.solution.domain;

import java.time.OffsetDateTime;

/** 部署方案主体；具体执行顺序和资产版本由子步骤保存。 */
public class DeploymentSolution {
    private Long id;
    private Long projectId;
    private Long environmentFingerprintId;
    private String fingerprintName;
    private String name;
    private String scenario;
    private String architectureDescription;
    private String prerequisites;
    private String rollbackSteps;
    private String riskNotes;
    private DeploymentSolutionStatus status;
    private Integer stepCount;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getEnvironmentFingerprintId() { return environmentFingerprintId; }
    public void setEnvironmentFingerprintId(Long environmentFingerprintId) { this.environmentFingerprintId = environmentFingerprintId; }
    public String getFingerprintName() { return fingerprintName; }
    public void setFingerprintName(String fingerprintName) { this.fingerprintName = fingerprintName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getArchitectureDescription() { return architectureDescription; }
    public void setArchitectureDescription(String architectureDescription) { this.architectureDescription = architectureDescription; }
    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
    public String getRollbackSteps() { return rollbackSteps; }
    public void setRollbackSteps(String rollbackSteps) { this.rollbackSteps = rollbackSteps; }
    public String getRiskNotes() { return riskNotes; }
    public void setRiskNotes(String riskNotes) { this.riskNotes = riskNotes; }
    public DeploymentSolutionStatus getStatus() { return status; }
    public void setStatus(DeploymentSolutionStatus status) { this.status = status; }
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
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
