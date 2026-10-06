package com.company.projectmanagement.report.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GenerateWeeklyReportRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @Size(max = 20000) String nextWeekPlan) { }
