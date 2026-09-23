package com.company.projectmanagement.record.web;

import java.util.List;

public record SimilarDeploymentResponse(
        DeploymentRecordSummaryResponse record,
        int score,
        List<String> matchedFields,
        List<String> differentFields) { }
