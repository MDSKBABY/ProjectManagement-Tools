package com.company.projectmanagement.solution.web;

import com.company.projectmanagement.solution.domain.DeploymentSolution;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record DeploymentSolutionResponse(
        Long id, Long projectId, String name, String scenario, Long fingerprintId,
        String fingerprintName, String architectureDescription, String prerequisites,
        String rollbackSteps, String riskNotes, DeploymentSolutionStatus status,
        List<DeploymentSolutionStepResponse> steps, OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static DeploymentSolutionResponse from(
            DeploymentSolution solution, List<DeploymentSolutionStepResponse> steps) {
        return new DeploymentSolutionResponse(
                solution.getId(), solution.getProjectId(), solution.getName(), solution.getScenario(),
                solution.getEnvironmentFingerprintId(), solution.getFingerprintName(),
                solution.getArchitectureDescription(), solution.getPrerequisites(),
                solution.getRollbackSteps(), solution.getRiskNotes(), solution.getStatus(),
                steps, solution.getCreatedAt(), solution.getUpdatedAt());
    }
}
