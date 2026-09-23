package com.company.projectmanagement.environment.domain;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import java.time.OffsetDateTime;

/** 可规范化检索的项目环境快照。 */
public class EnvironmentFingerprint {
    private Long id;
    private Long projectId;
    private String name;
    private DeploymentEnvironment environment;
    private String operatingSystem;
    private String osVersion;
    private String kernelVersion;
    private String architecture;
    private String runtimeName;
    private String runtimeVersion;
    private String databaseName;
    private String databaseVersion;
    private String middlewaresJson;
    private String networkZone;
    private String tagsJson;
    private String notes;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DeploymentEnvironment getEnvironment() { return environment; }
    public void setEnvironment(DeploymentEnvironment environment) { this.environment = environment; }
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getKernelVersion() { return kernelVersion; }
    public void setKernelVersion(String kernelVersion) { this.kernelVersion = kernelVersion; }
    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
    public String getRuntimeName() { return runtimeName; }
    public void setRuntimeName(String runtimeName) { this.runtimeName = runtimeName; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getDatabaseVersion() { return databaseVersion; }
    public void setDatabaseVersion(String databaseVersion) { this.databaseVersion = databaseVersion; }
    public String getMiddlewaresJson() { return middlewaresJson; }
    public void setMiddlewaresJson(String middlewaresJson) { this.middlewaresJson = middlewaresJson; }
    public String getNetworkZone() { return networkZone; }
    public void setNetworkZone(String networkZone) { this.networkZone = networkZone; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
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
