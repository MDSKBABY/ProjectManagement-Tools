package com.company.projectmanagement.server.web;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.server.domain.ServerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建或全量更新服务器档案的输入边界。 */
public record SaveServerRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 253)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        @NotNull DeploymentEnvironment environment,
        @NotNull ServerStatus status,
        @Size(max = 100) String operatingSystem,
        @Size(max = 64) String architecture,
        @Size(max = 200) String purpose,
        @Size(max = 5000) String description) {
}
