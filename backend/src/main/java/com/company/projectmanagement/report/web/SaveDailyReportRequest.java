package com.company.projectmanagement.report.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaveDailyReportRequest(
        @NotNull LocalDate reportDate,
        @NotBlank @Size(max = 20000) String originalContent,
        @Size(max = 20000) String polishedContent,
        @NotNull @DecimalMin("0.0") @DecimalMax("24.0") BigDecimal workHours,
        List<Long> workItemIds,
        List<Long> meetingRecordIds,
        List<Long> deploymentRecordIds) { }
