package com.company.projectmanagement.report.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.report.domain.DailyReportStatus;
import com.company.projectmanagement.report.domain.WeeklyReport;
import com.company.projectmanagement.report.mapper.WeeklyReportMapper;
import com.company.projectmanagement.report.web.DailyReportSnapshot;
import com.company.projectmanagement.report.web.GenerateWeeklyReportRequest;
import com.company.projectmanagement.report.web.UpdateWeeklyReportRequest;
import com.company.projectmanagement.report.web.WeeklyReportResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WeeklyReportService {
    private static final TypeReference<List<DailyReportSnapshot>> SNAPSHOT_TYPE = new TypeReference<>() { };
    private final WeeklyReportMapper mapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final ObjectMapper objectMapper;

    public WeeklyReportService(WeeklyReportMapper mapper, ProjectMapper projectMapper,
                               AppUserMapper appUserMapper, IdentityAccessMapper identityAccessMapper,
                               ObjectMapper objectMapper) {
        this.mapper = mapper; this.projectMapper = projectMapper; this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper; this.objectMapper = objectMapper;
    }

    public List<WeeklyReportResponse> list(Long projectId, String username) {
        Actor actor = requireActor(username); requireVisible(projectId, actor);
        return mapper.selectList(projectId).stream().map(this::response).toList();
    }

    public WeeklyReportResponse detail(Long projectId, Long reportId, String username) {
        Actor actor = requireActor(username); requireVisible(projectId, actor);
        return response(requireReport(projectId, reportId));
    }

    @Transactional
    public WeeklyReportResponse generate(Long projectId, GenerateWeeklyReportRequest request, String username) {
        Actor actor = requireActor(username); requireWritable(projectId, actor);
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "周报开始日期不能晚于结束日期");
        }
        List<DailyReportSnapshot> snapshots = mapper.selectConfirmedDailySnapshots(
                projectId, request.periodStart(), request.periodEnd());
        if (snapshots.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFIRMED_DAILY_REPORT_REQUIRED", "所选周期内没有已确认日报");
        }
        WeeklyReport report = new WeeklyReport();
        report.setProjectId(projectId); report.setPeriodStart(request.periodStart());
        report.setPeriodEnd(request.periodEnd()); report.setGenerationMethod("AUTO_SUMMARY");
        report.setContent(buildContent(snapshots));
        report.setNextWeekPlan(normalize(request.nextWeekPlan()));
        report.setDailySnapshotJson(writeSnapshots(snapshots));
        report.setCreatedBy(actor.user().getId()); report.setUpdatedBy(actor.user().getId());
        mapper.insert(report);
        mapper.recordAudit(actor.user().getId(), "WEEKLY_REPORT_GENERATED", report.getId().toString(), projectId);
        return response(requireReport(projectId, report.getId()));
    }

    @Transactional
    public WeeklyReportResponse update(Long projectId, Long reportId,
                                       UpdateWeeklyReportRequest request, String username) {
        Actor actor = requireActor(username); requireWritable(projectId, actor);
        WeeklyReport report = requireReport(projectId, reportId);
        if (report.getStatus() != DailyReportStatus.DRAFT) throw locked();
        report.setContent(request.content().trim());
        report.setNextWeekPlan(normalize(request.nextWeekPlan()));
        report.setUpdatedBy(actor.user().getId());
        if (mapper.update(report) == 0) throw locked();
        mapper.recordAudit(actor.user().getId(), "WEEKLY_REPORT_UPDATED", reportId.toString(), projectId);
        return response(requireReport(projectId, reportId));
    }

    @Transactional
    public WeeklyReportResponse confirm(Long projectId, Long reportId, String username) {
        Actor actor = requireActor(username); requireVisible(projectId, actor);
        requireReport(projectId, reportId);
        if (mapper.confirm(projectId, reportId, actor.user().getId()) == 0) throw locked();
        mapper.recordAudit(actor.user().getId(), "WEEKLY_REPORT_CONFIRMED", reportId.toString(), projectId);
        return response(requireReport(projectId, reportId));
    }

    private String buildContent(List<DailyReportSnapshot> snapshots) {
        BigDecimal hours = snapshots.stream().map(DailyReportSnapshot::workHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder text = new StringBuilder("本周完成工作（共 ").append(hours.stripTrailingZeros().toPlainString()).append(" 小时）：");
        snapshots.forEach(item -> text.append("\n- ").append(item.reportDate()).append(" · ")
                .append(item.reporterDisplayName()).append("：").append(item.content()));
        return text.toString();
    }

    private WeeklyReportResponse response(WeeklyReport report) {
        try {
            return WeeklyReportResponse.from(report, objectMapper.readValue(report.getDailySnapshotJson(), SNAPSHOT_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取周报日报快照", exception);
        }
    }

    private String writeSnapshots(List<DailyReportSnapshot> snapshots) {
        try { return objectMapper.writeValueAsString(snapshots); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法保存日报快照", exception); }
    }

    private WeeklyReport requireReport(Long projectId, Long reportId) {
        WeeklyReport report = mapper.selectById(projectId, reportId);
        if (report == null) throw new ApiException(HttpStatus.NOT_FOUND, "WEEKLY_REPORT_NOT_FOUND", "周报不存在");
        return report;
    }
    private void requireVisible(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(projectId, actor.user().getId(), actor.admin()) == null)
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }
    private void requireWritable(Long projectId, Actor actor) {
        requireVisible(projectId, actor);
        if (!actor.admin() && !projectMapper.canWriteFiles(projectId, actor.user().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "WEEKLY_REPORT_ACCESS_DENIED", "无权维护该项目周报");
    }
    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        return new Actor(user, identityAccessMapper.selectRoleCodesByUserId(user.getId()).contains("ADMIN"));
    }
    private static String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static ApiException locked() { return new ApiException(HttpStatus.CONFLICT, "WEEKLY_REPORT_LOCKED", "周报已确认，不能再修改"); }
    private record Actor(AppUser user, boolean admin) { }
}
