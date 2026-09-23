package com.company.projectmanagement.solution.web;

import com.company.projectmanagement.solution.domain.DeploymentSolution;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStatus;
import java.time.OffsetDateTime;

public record DeploymentSolutionSummaryResponse(
        Long id, Long projectId, String name, String scenario, Long fingerprintId,
        String fingerprintName, DeploymentSolutionStatus status, Integer stepCount,
        OffsetDateTime updatedAt) {
    public static DeploymentSolutionSummaryResponse from(DeploymentSolution solution) {
        return new DeploymentSolutionSummaryResponse(
                solution.getId(), solution.getProjectId(), solution.getName(), solution.getScenario(),
                solution.getEnvironmentFingerprintId(), solution.getFingerprintName(),
                solution.getStatus(), solution.getStepCount(), solution.getUpdatedAt());
    }
}
