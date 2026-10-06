package com.company.projectmanagement.report.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 日报持久化模型；确认后的内容由数据库状态和服务层共同保护。 */
public class DailyReport {
    private Long id;
    private Long projectId;
    private LocalDate reportDate;
    private Long reporterId;
    private String reporterDisplayName;
    private String originalContent;
    private String polishedContent;
    private BigDecimal workHours;
    private DailyReportStatus status;
    private Long confirmedBy;
    private String confirmedByDisplayName;
    private OffsetDateTime confirmedAt;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long value) { projectId = value; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate value) { reportDate = value; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long value) { reporterId = value; }
    public String getReporterDisplayName() { return reporterDisplayName; }
    public void setReporterDisplayName(String value) { reporterDisplayName = value; }
    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String value) { originalContent = value; }
    public String getPolishedContent() { return polishedContent; }
    public void setPolishedContent(String value) { polishedContent = value; }
    public BigDecimal getWorkHours() { return workHours; }
    public void setWorkHours(BigDecimal value) { workHours = value; }
    public DailyReportStatus getStatus() { return status; }
    public void setStatus(DailyReportStatus value) { status = value; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long value) { confirmedBy = value; }
    public String getConfirmedByDisplayName() { return confirmedByDisplayName; }
    public void setConfirmedByDisplayName(String value) { confirmedByDisplayName = value; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime value) { confirmedAt = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long value) { updatedBy = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
}
