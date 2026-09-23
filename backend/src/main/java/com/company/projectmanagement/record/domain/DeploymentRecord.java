package com.company.projectmanagement.record.domain;

import java.time.OffsetDateTime;

/** 不可覆盖的部署执行记录，三个 JSON 字段保存执行时业务快照。 */
public class DeploymentRecord {
    private Long id;
    private Long projectId;
    private Long serverId;
    private String serverName;
    private Long solutionId;
    private String solutionName;
    private Long environmentFingerprintId;
    private DeploymentResult result;
    private Long executedBy;
    private String executedByName;
    private OffsetDateTime executedAt;
    private String exceptionNotes;
    private String notes;
    private Boolean baseline;
    private String serverSnapshotJson;
    private String environmentSnapshotJson;
    private String solutionSnapshotJson;
    private Long createdBy;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getServerId() { return serverId; }
    public void setServerId(Long serverId) { this.serverId = serverId; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public Long getSolutionId() { return solutionId; }
    public void setSolutionId(Long solutionId) { this.solutionId = solutionId; }
    public String getSolutionName() { return solutionName; }
    public void setSolutionName(String solutionName) { this.solutionName = solutionName; }
    public Long getEnvironmentFingerprintId() { return environmentFingerprintId; }
    public void setEnvironmentFingerprintId(Long value) { this.environmentFingerprintId = value; }
    public DeploymentResult getResult() { return result; }
    public void setResult(DeploymentResult result) { this.result = result; }
    public Long getExecutedBy() { return executedBy; }
    public void setExecutedBy(Long executedBy) { this.executedBy = executedBy; }
    public String getExecutedByName() { return executedByName; }
    public void setExecutedByName(String executedByName) { this.executedByName = executedByName; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
    public String getExceptionNotes() { return exceptionNotes; }
    public void setExceptionNotes(String exceptionNotes) { this.exceptionNotes = exceptionNotes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Boolean getBaseline() { return baseline; }
    public void setBaseline(Boolean baseline) { this.baseline = baseline; }
    public String getServerSnapshotJson() { return serverSnapshotJson; }
    public void setServerSnapshotJson(String value) { this.serverSnapshotJson = value; }
    public String getEnvironmentSnapshotJson() { return environmentSnapshotJson; }
    public void setEnvironmentSnapshotJson(String value) { this.environmentSnapshotJson = value; }
    public String getSolutionSnapshotJson() { return solutionSnapshotJson; }
    public void setSolutionSnapshotJson(String value) { this.solutionSnapshotJson = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
