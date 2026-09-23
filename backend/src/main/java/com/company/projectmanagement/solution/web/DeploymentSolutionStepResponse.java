package com.company.projectmanagement.solution.web;

import com.company.projectmanagement.solution.domain.DeploymentSolutionStep;

public record DeploymentSolutionStepResponse(
        Long id,
        Integer stepOrder,
        String title,
        String instructions,
        Long assetId,
        String assetName,
        Integer assetVersion,
        String assetVersionLabel,
        String parametersTemplate) {
    public static DeploymentSolutionStepResponse from(DeploymentSolutionStep step) {
        return new DeploymentSolutionStepResponse(
                step.getId(), step.getStepOrder(), step.getTitle(), step.getInstructions(),
                step.getDeploymentAssetId(), step.getAssetName(), step.getAssetVersion(),
                step.getAssetVersionLabel(), step.getParametersTemplate());
    }
}
