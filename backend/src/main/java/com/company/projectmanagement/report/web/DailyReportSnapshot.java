package com.company.projectmanagement.report.web;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyReportSnapshot(
        Long reportId, LocalDate reportDate, Long reporterId,
        String reporterDisplayName, String content, BigDecimal workHours) { }
