package com.company.projectmanagement.server.domain;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import java.time.OffsetDateTime;

/** 项目服务器档案；凭据由独立表保存，普通查询只暴露是否已配置。 */
public class ServerRecord {
    private Long id;
    private Long projectId;
    private String name;
    private String host;
    private Integer port;
    private DeploymentEnvironment environment;
    private ServerStatus status;
    private String operatingSystem;
    private String architecture;
    private String purpose;
    private String description;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
    private Boolean credentialConfigured;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public DeploymentEnvironment getEnvironment() { return environment; }
    public void setEnvironment(DeploymentEnvironment environment) { this.environment = environment; }
    public ServerStatus getStatus() { return status; }
    public void setStatus(ServerStatus status) { this.status = status; }
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
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
    public Boolean getCredentialConfigured() { return credentialConfigured; }
    public void setCredentialConfigured(Boolean credentialConfigured) { this.credentialConfigured = credentialConfigured; }
}
