package com.company.projectmanagement.solution.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveDeploymentSolutionStepRequest(
        @NotNull @Min(1) Integer stepOrder,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String instructions,
        @NotNull Long assetId,
        @Size(max = 5000) String parametersTemplate) {
}
