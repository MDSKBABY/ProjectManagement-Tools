package com.company.projectmanagement.report.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.record.mapper.DeploymentRecordMapper;
import com.company.projectmanagement.report.ai.DailyReportPolisher;
import com.company.projectmanagement.report.domain.DailyReport;
import com.company.projectmanagement.report.domain.DailyReportStatus;
import com.company.projectmanagement.report.mapper.DailyReportMapper;
import com.company.projectmanagement.report.web.DailyReportResponse;
import com.company.projectmanagement.report.web.SaveDailyReportRequest;
import com.company.projectmanagement.workitem.mapper.WorkItemMapper;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DailyReportService {
    private final DailyReportMapper mapper;
    private final ProjectMapper projectMapper;
    private final WorkItemMapper workItemMapper;
    private final DeploymentRecordMapper deploymentRecordMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final DailyReportPolisher polisher;

    public DailyReportService(
            DailyReportMapper mapper, ProjectMapper projectMapper,
            WorkItemMapper workItemMapper, DeploymentRecordMapper deploymentRecordMapper,
            AppUserMapper appUserMapper, IdentityAccessMapper identityAccessMapper,
            DailyReportPolisher polisher) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
        this.workItemMapper = workItemMapper;
        this.deploymentRecordMapper = deploymentRecordMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.polisher = polisher;
    }

    public List<DailyReportResponse> list(
            Long projectId, LocalDate from, LocalDate to, Long reporterId, String username) {
        Actor actor = requireActor(username);
        requireVisibleProject(projectId, actor);
        return mapper.selectList(projectId, from, to, reporterId).stream()
                .map(this::response).toList();
    }

    public DailyReportResponse detail(Long projectId, Long reportId, String username) {
        Actor actor = requireActor(username);
        requireVisibleProject(projectId, actor);
        return response(requireReport(projectId, reportId));
    }

    @Transactional
    public DailyReportResponse create(
            Long projectId, SaveDailyReportRequest request, String username) {
        Actor actor = requireActor(username);
        requireWritableProject(projectId, actor);
        List<Long> workItemIds = validateWorkItems(projectId, request.workItemIds());
        List<Long> meetingRecordIds = validateMeetingRecords(projectId, request.meetingRecordIds());
        List<Long> deploymentIds = validateDeployments(projectId, request.deploymentRecordIds());
        DailyReport report = map(request, new DailyReport());
        report.setProjectId(projectId);
        report.setReporterId(actor.user().getId());
        report.setCreatedBy(actor.user().getId());
        report.setUpdatedBy(actor.user().getId());
        try {
            mapper.insert(report);
        } catch (DuplicateKeyException exception) {
            throw duplicateReport();
        }
        replaceLinks(report.getId(), workItemIds, meetingRecordIds, deploymentIds);
        mapper.recordAudit(actor.user().getId(), "DAILY_REPORT_CREATED",
                report.getId().toString(), projectId);
        return response(requireReport(projectId, report.getId()));
    }

    @Transactional
    public DailyReportResponse update(
            Long projectId, Long reportId, SaveDailyReportRequest request, String username) {
        Actor actor = requireActor(username);
        requireWritableProject(projectId, actor);
        DailyReport current = requireReport(projectId, reportId);
        if (current.getStatus() != DailyReportStatus.DRAFT) {
            throw locked();
        }
        if (!current.getReporterId().equals(actor.user().getId()) && !actor.administrator()
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DAILY_REPORT_ACCESS_DENIED", "只能修改自己的日报草稿");
        }
        List<Long> workItemIds = validateWorkItems(projectId, request.workItemIds());
        List<Long> meetingRecordIds = validateMeetingRecords(projectId, request.meetingRecordIds());
        List<Long> deploymentIds = validateDeployments(projectId, request.deploymentRecordIds());
        DailyReport changed = map(request, current);
        changed.setUpdatedBy(actor.user().getId());
        try {
            if (mapper.update(changed) == 0) {
                throw locked();
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateReport();
        }
        replaceLinks(reportId, workItemIds, meetingRecordIds, deploymentIds);
        mapper.recordAudit(actor.user().getId(), "DAILY_REPORT_UPDATED", reportId.toString(), projectId);
        return response(requireReport(projectId, reportId));
    }

    @Transactional
    public DailyReportResponse confirm(Long projectId, Long reportId, String username) {
        Actor actor = requireActor(username);
        requireVisibleProject(projectId, actor);
        requireReport(projectId, reportId);
        if (mapper.confirm(projectId, reportId, actor.user().getId()) == 0) {
            throw locked();
        }
        mapper.recordAudit(actor.user().getId(), "DAILY_REPORT_CONFIRMED", reportId.toString(), projectId);
        return response(requireReport(projectId, reportId));
    }

    @Transactional
    public DailyReportResponse polish(Long projectId, Long reportId, String username) {
        Actor actor = requireActor(username);
        requireWritableProject(projectId, actor);
        DailyReport report = requireReport(projectId, reportId);
        if (report.getStatus() != DailyReportStatus.DRAFT) {
            throw locked();
        }
        if (!report.getReporterId().equals(actor.user().getId()) && !actor.administrator()
                && !projectMapper.canManage(projectId, actor.user().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DAILY_REPORT_ACCESS_DENIED", "只能润色自己的日报草稿");
        }
        String polished = polisher.polish(report.getOriginalContent());
        savePolished(projectId, reportId, actor.user().getId(), polished);
        return response(requireReport(projectId, reportId));
    }

    private void savePolished(Long projectId, Long reportId, Long actorUserId, String polished) {
        if (mapper.updatePolishedContent(projectId, reportId, polished, actorUserId) == 0) {
            throw locked();
        }
        mapper.recordAudit(actorUserId, "DAILY_REPORT_POLISHED", reportId.toString(), projectId);
    }

    private DailyReport map(SaveDailyReportRequest request, DailyReport report) {
        report.setReportDate(request.reportDate());
        report.setOriginalContent(request.originalContent().trim());
        report.setPolishedContent(StringUtils.hasText(request.polishedContent())
                ? request.polishedContent().trim() : null);
        report.setWorkHours(request.workHours());
        return report;
    }

    private List<Long> validateWorkItems(Long projectId, List<Long> requested) {
        List<Long> ids = distinct(requested);
        ids.forEach(id -> {
            if (workItemMapper.selectById(projectId, id) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "关联工作项不存在");
            }
        });
        return ids;
    }

    private List<Long> validateDeployments(Long projectId, List<Long> requested) {
        List<Long> ids = distinct(requested);
        ids.forEach(id -> {
            if (deploymentRecordMapper.selectById(projectId, id) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_RECORD_NOT_FOUND", "关联部署记录不存在");
            }
        });
        return ids;
    }

    private List<Long> validateMeetingRecords(Long projectId, List<Long> requested) {
        List<Long> ids = distinct(requested);
        ids.forEach(id -> {
            if (mapper.countMeetingRecord(projectId, id) == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEETING_RECORD_NOT_FOUND", "关联会议或培训记录不存在");
            }
        });
        return ids;
    }

    private void replaceLinks(
            Long reportId, List<Long> workItemIds,
            List<Long> meetingRecordIds, List<Long> deploymentIds) {
        mapper.deleteWorkItems(reportId);
        mapper.deleteMeetingRecords(reportId);
        mapper.deleteDeploymentRecords(reportId);
        workItemIds.forEach(id -> mapper.linkWorkItem(reportId, id));
        meetingRecordIds.forEach(id -> mapper.linkMeetingRecord(reportId, id));
        deploymentIds.forEach(id -> mapper.linkDeploymentRecord(reportId, id));
    }

    private DailyReportResponse response(DailyReport report) {
        return DailyReportResponse.from(report, mapper.selectWorkItems(report.getId()),
                mapper.selectMeetingRecords(report.getId()),
                mapper.selectDeploymentRecords(report.getId()));
    }

    private DailyReport requireReport(Long projectId, Long reportId) {
        DailyReport report = mapper.selectById(projectId, reportId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DAILY_REPORT_NOT_FOUND", "日报不存在");
        }
        return report;
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(projectId, actor.user().getId(), actor.administrator()) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        requireVisibleProject(projectId, actor);
        if (!actor.administrator() && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DAILY_REPORT_ACCESS_DENIED", "无权维护该项目日报");
        }
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        return new Actor(user, identityAccessMapper.selectRoleCodesByUserId(user.getId()).contains("ADMIN"));
    }

    private static List<Long> distinct(List<Long> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private static ApiException locked() {
        return new ApiException(HttpStatus.CONFLICT, "DAILY_REPORT_LOCKED", "日报已确认，不能再修改");
    }

    private static ApiException duplicateReport() {
        return new ApiException(HttpStatus.CONFLICT, "DAILY_REPORT_ALREADY_EXISTS", "该日期已存在日报");
    }

    private record Actor(AppUser user, boolean administrator) { }
}
