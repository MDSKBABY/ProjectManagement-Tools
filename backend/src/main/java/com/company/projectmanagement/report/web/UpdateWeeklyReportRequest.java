package com.company.projectmanagement.report.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWeeklyReportRequest(
        @NotBlank @Size(max = 30000) String content,
        @Size(max = 20000) String nextWeekPlan) { }
