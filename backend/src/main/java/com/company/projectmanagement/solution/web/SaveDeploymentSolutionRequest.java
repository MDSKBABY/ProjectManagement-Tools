package com.company.projectmanagement.solution.web;

import com.company.projectmanagement.solution.domain.DeploymentSolutionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 方案和步骤作为一个聚合全量保存，避免部分编排状态外泄。 */
public record SaveDeploymentSolutionRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 1000) String scenario,
        @NotNull Long fingerprintId,
        @NotBlank @Size(max = 10000) String architectureDescription,
        @NotBlank @Size(max = 10000) String prerequisites,
        @NotBlank @Size(max = 10000) String rollbackSteps,
        @NotBlank @Size(max = 10000) String riskNotes,
        @NotNull DeploymentSolutionStatus status,
        @NotNull @Size(min = 1, max = 50) List<@Valid SaveDeploymentSolutionStepRequest> steps) {
}
