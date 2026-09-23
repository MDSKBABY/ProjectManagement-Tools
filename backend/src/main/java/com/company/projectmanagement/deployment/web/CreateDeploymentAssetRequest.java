package com.company.projectmanagement.deployment.web;

import com.company.projectmanagement.deployment.domain.DeploymentAssetType;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.deployment.domain.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建全新部署资产或同一资产组的下一不可变版本。 */
public record CreateDeploymentAssetRequest(
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String assetGroupId,
        @NotBlank @Size(max = 200) String name,
        @NotNull DeploymentAssetType assetType,
        @NotBlank @Size(max = 64) String versionLabel,
        @NotNull Long fileAssetId,
        @Size(max = 100) String operatingSystem,
        @Size(max = 64) String architecture,
        @NotNull DeploymentEnvironment environment,
        @NotNull RiskLevel riskLevel,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
        @Size(max = 5000) String description,
        @Size(max = 5000) String prerequisites,
        @Size(max = 5000) String executionInstructions,
        @Size(max = 5000) String rollbackInstructions) {
}
