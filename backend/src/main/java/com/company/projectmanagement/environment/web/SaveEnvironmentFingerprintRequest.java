package com.company.projectmanagement.environment.web;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建或全量更新环境指纹的边界输入。 */
public record SaveEnvironmentFingerprintRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull DeploymentEnvironment environment,
        @NotBlank @Size(max = 100) String operatingSystem,
        @Size(max = 100) String osVersion,
        @Size(max = 100) String kernelVersion,
        @NotBlank @Size(max = 64) String architecture,
        @Size(max = 100) String runtimeName,
        @Size(max = 100) String runtimeVersion,
        @Size(max = 100) String databaseName,
        @Size(max = 100) String databaseVersion,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> middlewares,
        @Size(max = 100) String networkZone,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
        @Size(max = 5000) String notes) {
}
