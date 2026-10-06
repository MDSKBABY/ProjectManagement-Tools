package com.company.projectmanagement.lifecycle.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.lifecycle.web.LifecycleNodeResponse;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProjectLifecycleService {
    private final JdbcTemplate jdbc;
    private final ProjectMapper projects;
    private final AppUserMapper users;
    private final IdentityAccessMapper access;
    private final Clock clock = Clock.systemDefaultZone();

    public ProjectLifecycleService(
            JdbcTemplate jdbc,
            ProjectMapper projects,
            AppUserMapper users,
            IdentityAccessMapper access) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.users = users;
        this.access = access;
    }

    public List<LifecycleNodeResponse> list(long projectId, LocalDate from, LocalDate to, String username) {
        AppUser user = users.selectActiveByUsername(username);
        boolean admin = user != null && access.selectRoleCodesByUserId(user.getId()).contains("ADMIN");
        if (user == null || projects.selectVisibleById(projectId, user.getId(), admin) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "开始日期不能晚于结束日期");
        }

        List<LifecycleNodeResponse> result = new ArrayList<>();
        result.addAll(query("""
            SELECT 'MILESTONE' type,id,title,COALESCE(planned_end_date,planned_start_date,created_at::date) event_date,status business_status,description
            FROM work_item WHERE project_id=? AND type='MILESTONE' AND deleted_at IS NULL
            AND COALESCE(planned_end_date,planned_start_date,created_at::date) BETWEEN ? AND ?
            """, projectId, from, to));
        result.addAll(query("""
            SELECT 'DEPLOYMENT' type,id,CONCAT('部署：',result) title,executed_at::date event_date,result business_status,COALESCE(notes,exception_notes) description
            FROM deployment_record WHERE project_id=? AND executed_at::date BETWEEN ? AND ?
            """, projectId, from, to));
        result.addAll(query("""
            SELECT 'MEETING' type,id,title,occurred_at::date event_date,status business_status,minutes description
            FROM meeting_record WHERE project_id=? AND deleted_at IS NULL AND occurred_at::date BETWEEN ? AND ?
            """, projectId, from, to));
        result.addAll(query("""
            SELECT 'DAILY_REPORT' type,report.id,CONCAT('日报 · ',reporter.display_name) title,report_date event_date,report.status business_status,COALESCE(polished_content,original_content) description
            FROM daily_report report JOIN app_user reporter ON reporter.id=report.reporter_id
            WHERE report.project_id=? AND report.status='CONFIRMED' AND report.deleted_at IS NULL AND report_date BETWEEN ? AND ?
            """, projectId, from, to));
        result.addAll(query("""
            SELECT 'WEEKLY_REPORT' type,id,'项目周报' title,period_end event_date,status business_status,content description
            FROM weekly_report WHERE project_id=? AND status='CONFIRMED' AND deleted_at IS NULL AND period_end BETWEEN ? AND ?
            """, projectId, from, to));
        return result.stream()
                .sorted(Comparator.comparing(LifecycleNodeResponse::date)
                        .thenComparing(LifecycleNodeResponse::type))
                .toList();
    }

    private List<LifecycleNodeResponse> query(String sql, long projectId, LocalDate from, LocalDate to) {
        return jdbc.query(
                sql,
                (resultSet, rowNumber) -> {
                    LocalDate date = resultSet.getObject("event_date", LocalDate.class);
                    String type = resultSet.getString("type");
                    String businessStatus = resultSet.getString("business_status");
                    return new LifecycleNodeResponse(
                            type,
                            resultSet.getLong("id"),
                            resultSet.getString("title"),
                            date,
                            schedule(type, date, businessStatus),
                            businessStatus,
                            resultSet.getString("description"));
                },
                projectId,
                Date.valueOf(from),
                Date.valueOf(to));
    }

    private String schedule(String type, LocalDate date, String businessStatus) {
        if (!"MILESTONE".equals(type) || "DONE".equals(businessStatus) || "CANCELED".equals(businessStatus)) {
            return "NORMAL";
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), date);
        if (days < 0) {
            return "OVERDUE";
        }
        if (days <= 7) {
            return "DUE_SOON";
        }
        return "NORMAL";
    }
}
