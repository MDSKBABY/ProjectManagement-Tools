package com.company.projectmanagement.report.web;

import com.company.projectmanagement.report.domain.DailyReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyReportResponse(
        Long id,
        Long projectId,
        LocalDate reportDate,
        Long reporterId,
        String reporterDisplayName,
        String originalContent,
        String polishedContent,
        BigDecimal workHours,
        String status,
        Long confirmedBy,
        String confirmedByDisplayName,
        OffsetDateTime confirmedAt,
        List<DailyReportLinkResponse> workItems,
        List<DailyReportLinkResponse> meetingRecords,
        List<DailyReportLinkResponse> deploymentRecords,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static DailyReportResponse from(
            DailyReport report,
            List<DailyReportLinkResponse> workItems,
            List<DailyReportLinkResponse> meetingRecords,
            List<DailyReportLinkResponse> deploymentRecords) {
        return new DailyReportResponse(
                report.getId(), report.getProjectId(), report.getReportDate(),
                report.getReporterId(), report.getReporterDisplayName(),
                report.getOriginalContent(), report.getPolishedContent(), report.getWorkHours(),
                report.getStatus().name(), report.getConfirmedBy(),
                report.getConfirmedByDisplayName(), report.getConfirmedAt(),
                workItems, meetingRecords, deploymentRecords,
                report.getCreatedAt(), report.getUpdatedAt());
    }
}
