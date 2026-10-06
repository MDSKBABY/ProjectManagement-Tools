package com.company.projectmanagement.report.web;

import com.company.projectmanagement.report.domain.WeeklyReport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WeeklyReportResponse(
        Long id, Long projectId, LocalDate periodStart, LocalDate periodEnd,
        String content, String nextWeekPlan, String generationMethod, String status,
        List<DailyReportSnapshot> dailySnapshots, Long confirmedBy,
        String confirmedByDisplayName, OffsetDateTime confirmedAt,
        Long createdBy, String createdByDisplayName, OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public static WeeklyReportResponse from(WeeklyReport r, List<DailyReportSnapshot> snapshots) {
        return new WeeklyReportResponse(r.getId(), r.getProjectId(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getContent(), r.getNextWeekPlan(), r.getGenerationMethod(), r.getStatus().name(),
                snapshots, r.getConfirmedBy(), r.getConfirmedByDisplayName(), r.getConfirmedAt(),
                r.getCreatedBy(), r.getCreatedByDisplayName(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
