package com.company.projectmanagement.server.web;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.server.domain.ServerRecord;
import com.company.projectmanagement.server.domain.ServerStatus;
import java.time.OffsetDateTime;

/** 安全的服务器档案响应，只说明凭据是否存在。 */
public record ServerResponse(
        Long id,
        Long projectId,
        String name,
        String host,
        Integer port,
        DeploymentEnvironment environment,
        ServerStatus status,
        String operatingSystem,
        String architecture,
        String purpose,
        String description,
        boolean credentialConfigured,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ServerResponse from(ServerRecord server) {
        return new ServerResponse(
                server.getId(),
                server.getProjectId(),
                server.getName(),
                server.getHost(),
                server.getPort(),
                server.getEnvironment(),
                server.getStatus(),
                server.getOperatingSystem(),
                server.getArchitecture(),
                server.getPurpose(),
                server.getDescription(),
                Boolean.TRUE.equals(server.getCredentialConfigured()),
                server.getCreatedAt(),
                server.getUpdatedAt());
    }
}
