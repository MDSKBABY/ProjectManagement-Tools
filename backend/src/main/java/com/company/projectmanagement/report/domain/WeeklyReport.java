package com.company.projectmanagement.report.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public class WeeklyReport {
    private Long id;
    private Long projectId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String content;
    private String nextWeekPlan;
    private String generationMethod;
    private String dailySnapshotJson;
    private DailyReportStatus status;
    private Long confirmedBy;
    private String confirmedByDisplayName;
    private OffsetDateTime confirmedAt;
    private Long createdBy;
    private String createdByDisplayName;
    private OffsetDateTime createdAt;
    private Long updatedBy;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; } public void setId(Long v) { id=v; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long v) { projectId=v; }
    public LocalDate getPeriodStart() { return periodStart; } public void setPeriodStart(LocalDate v) { periodStart=v; }
    public LocalDate getPeriodEnd() { return periodEnd; } public void setPeriodEnd(LocalDate v) { periodEnd=v; }
    public String getContent() { return content; } public void setContent(String v) { content=v; }
    public String getNextWeekPlan() { return nextWeekPlan; } public void setNextWeekPlan(String v) { nextWeekPlan=v; }
    public String getGenerationMethod() { return generationMethod; } public void setGenerationMethod(String v) { generationMethod=v; }
    public String getDailySnapshotJson() { return dailySnapshotJson; } public void setDailySnapshotJson(String v) { dailySnapshotJson=v; }
    public DailyReportStatus getStatus() { return status; } public void setStatus(DailyReportStatus v) { status=v; }
    public Long getConfirmedBy() { return confirmedBy; } public void setConfirmedBy(Long v) { confirmedBy=v; }
    public String getConfirmedByDisplayName() { return confirmedByDisplayName; } public void setConfirmedByDisplayName(String v) { confirmedByDisplayName=v; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; } public void setConfirmedAt(OffsetDateTime v) { confirmedAt=v; }
    public Long getCreatedBy() { return createdBy; } public void setCreatedBy(Long v) { createdBy=v; }
    public String getCreatedByDisplayName() { return createdByDisplayName; } public void setCreatedByDisplayName(String v) { createdByDisplayName=v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt=v; }
    public Long getUpdatedBy() { return updatedBy; } public void setUpdatedBy(Long v) { updatedBy=v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { updatedAt=v; }
}
