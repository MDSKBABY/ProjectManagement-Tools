package com.company.projectmanagement.report.mapper;

import com.company.projectmanagement.report.domain.WeeklyReport;
import com.company.projectmanagement.report.web.DailyReportSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WeeklyReportMapper {
    String COLUMNS = """
            report.id, report.project_id, report.period_start, report.period_end,
            report.content, report.next_week_plan, report.generation_method,
            report.daily_snapshot::text AS daily_snapshot_json, report.status,
            report.confirmed_by, confirmer.display_name AS confirmed_by_display_name,
            report.confirmed_at, report.created_by,
            creator.display_name AS created_by_display_name, report.created_at,
            report.updated_by, report.updated_at
            """;

    @Insert("""
            INSERT INTO weekly_report (
                project_id, period_start, period_end, content, next_week_plan,
                generation_method, daily_snapshot, status, created_by, updated_by
            ) VALUES (
                #{projectId}, #{periodStart}, #{periodEnd}, #{content}, #{nextWeekPlan},
                #{generationMethod}, CAST(#{dailySnapshotJson} AS jsonb), 'DRAFT',
                #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WeeklyReport report);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM weekly_report report
            JOIN app_user creator ON creator.id = report.created_by
            LEFT JOIN app_user confirmer ON confirmer.id = report.confirmed_by
            WHERE report.project_id = #{projectId} AND report.id = #{reportId}
              AND report.deleted_at IS NULL
            """)
    WeeklyReport selectById(@Param("projectId") Long projectId, @Param("reportId") Long reportId);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM weekly_report report
            JOIN app_user creator ON creator.id = report.created_by
            LEFT JOIN app_user confirmer ON confirmer.id = report.confirmed_by
            WHERE report.project_id = #{projectId} AND report.deleted_at IS NULL
            ORDER BY report.period_start DESC, report.id DESC
            """)
    List<WeeklyReport> selectList(@Param("projectId") Long projectId);

    @Select("""
            SELECT report.id AS report_id, report.report_date, report.reporter_id,
                   reporter.display_name AS reporter_display_name,
                   COALESCE(report.polished_content, report.original_content) AS content,
                   report.work_hours
            FROM daily_report report
            JOIN app_user reporter ON reporter.id = report.reporter_id
            WHERE report.project_id = #{projectId} AND report.status = 'CONFIRMED'
              AND report.deleted_at IS NULL
              AND report.report_date BETWEEN #{periodStart} AND #{periodEnd}
            ORDER BY report.report_date, report.id
            """)
    List<DailyReportSnapshot> selectConfirmedDailySnapshots(
            @Param("projectId") Long projectId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Update("""
            UPDATE weekly_report SET content = #{content}, next_week_plan = #{nextWeekPlan},
                updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{id}
              AND status = 'DRAFT' AND deleted_at IS NULL
            """)
    int update(WeeklyReport report);

    @Update("""
            UPDATE weekly_report SET status = 'CONFIRMED', confirmed_by = #{actorUserId},
                confirmed_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{reportId}
              AND status = 'DRAFT' AND deleted_at IS NULL
            """)
    int confirm(@Param("projectId") Long projectId, @Param("reportId") Long reportId,
                @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO audit_log (actor_user_id, action, resource_type, resource_id, outcome, details)
            VALUES (#{actorUserId}, #{action}, 'WEEKLY_REPORT', #{reportId}, 'SUCCESS',
                    jsonb_build_object('projectId', #{projectId}))
            """)
    int recordAudit(@Param("actorUserId") Long actorUserId, @Param("action") String action,
                    @Param("reportId") String reportId, @Param("projectId") Long projectId);
}
