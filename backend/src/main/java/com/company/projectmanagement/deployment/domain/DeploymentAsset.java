package com.company.projectmanagement.deployment.domain;

import com.company.projectmanagement.file.domain.FileStatus;
import java.time.OffsetDateTime;

/** 不可变部署资产版本；修改业务资料时创建同一版本组的下一版本。 */
public class DeploymentAsset {
    private Long id;
    private String assetGroupId;
    private Integer version;
    private Long projectId;
    private String name;
    private DeploymentAssetType assetType;
    private String versionLabel;
    private Long fileAssetId;
    private String operatingSystem;
    private String architecture;
    private DeploymentEnvironment environment;
    private RiskLevel riskLevel;
    private String tagsJson;
    private String description;
    private String prerequisites;
    private String executionInstructions;
    private String rollbackInstructions;
    private Long createdBy;
    private String createdByDisplayName;
    private OffsetDateTime createdAt;
    private String fileOriginalName;
    private Long fileSizeBytes;
    private FileStatus fileStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetGroupId() { return assetGroupId; }
    public void setAssetGroupId(String assetGroupId) { this.assetGroupId = assetGroupId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DeploymentAssetType getAssetType() { return assetType; }
    public void setAssetType(DeploymentAssetType assetType) { this.assetType = assetType; }
    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }
    public Long getFileAssetId() { return fileAssetId; }
    public void setFileAssetId(Long fileAssetId) { this.fileAssetId = fileAssetId; }
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
    public DeploymentEnvironment getEnvironment() { return environment; }
    public void setEnvironment(DeploymentEnvironment environment) { this.environment = environment; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
    public String getExecutionInstructions() { return executionInstructions; }
    public void setExecutionInstructions(String executionInstructions) { this.executionInstructions = executionInstructions; }
    public String getRollbackInstructions() { return rollbackInstructions; }
    public void setRollbackInstructions(String rollbackInstructions) { this.rollbackInstructions = rollbackInstructions; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedByDisplayName() { return createdByDisplayName; }
    public void setCreatedByDisplayName(String createdByDisplayName) { this.createdByDisplayName = createdByDisplayName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public String getFileOriginalName() { return fileOriginalName; }
    public void setFileOriginalName(String fileOriginalName) { this.fileOriginalName = fileOriginalName; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public FileStatus getFileStatus() { return fileStatus; }
    public void setFileStatus(FileStatus fileStatus) { this.fileStatus = fileStatus; }
}
