package com.company.projectmanagement.record.web;

import jakarta.validation.constraints.NotNull;

public record UpdateDeploymentBaselineRequest(@NotNull Boolean baseline) { }
