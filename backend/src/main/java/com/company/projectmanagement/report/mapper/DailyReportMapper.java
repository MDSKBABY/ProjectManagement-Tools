package com.company.projectmanagement.report.mapper;

import com.company.projectmanagement.report.domain.DailyReport;
import com.company.projectmanagement.report.web.DailyReportLinkResponse;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DailyReportMapper {
    String COLUMNS = """
            report.id, report.project_id, report.report_date, report.reporter_id,
            reporter.display_name AS reporter_display_name, report.original_content,
            report.polished_content, report.work_hours, report.status,
            report.confirmed_by, confirmer.display_name AS confirmed_by_display_name,
            report.confirmed_at, report.created_by, report.created_at,
            report.updated_by, report.updated_at
            """;

    @Insert("""
            INSERT INTO daily_report (
                project_id, report_date, reporter_id, original_content, polished_content,
                work_hours, status, created_by, updated_by
            ) VALUES (
                #{projectId}, #{reportDate}, #{reporterId}, #{originalContent},
                #{polishedContent}, #{workHours}, 'DRAFT', #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DailyReport report);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM daily_report report
            JOIN app_user reporter ON reporter.id = report.reporter_id
            LEFT JOIN app_user confirmer ON confirmer.id = report.confirmed_by
            WHERE report.project_id = #{projectId} AND report.id = #{reportId}
              AND report.deleted_at IS NULL
            """)
    DailyReport selectById(@Param("projectId") Long projectId, @Param("reportId") Long reportId);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM daily_report report
            JOIN app_user reporter ON reporter.id = report.reporter_id
            LEFT JOIN app_user confirmer ON confirmer.id = report.confirmed_by
            WHERE report.project_id = #{projectId} AND report.deleted_at IS NULL
            <if test='from != null'>AND report.report_date &gt;= #{from}</if>
            <if test='to != null'>AND report.report_date &lt;= #{to}</if>
            <if test='reporterId != null'>AND report.reporter_id = #{reporterId}</if>
            ORDER BY report.report_date DESC, report.id DESC
            </script>
            """)
    List<DailyReport> selectList(
            @Param("projectId") Long projectId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("reporterId") Long reporterId);

    @Update("""
            UPDATE daily_report SET report_date = #{reportDate},
                original_content = #{originalContent}, polished_content = #{polishedContent},
                work_hours = #{workHours}, updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{id}
              AND reporter_id = #{reporterId} AND status = 'DRAFT' AND deleted_at IS NULL
            """)
    int update(DailyReport report);

    @Update("""
            UPDATE daily_report SET polished_content = #{content}, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{reportId}
              AND status = 'DRAFT' AND deleted_at IS NULL
            """)
    int updatePolishedContent(
            @Param("projectId") Long projectId, @Param("reportId") Long reportId,
            @Param("content") String content, @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE daily_report SET status = 'CONFIRMED', confirmed_by = #{actorUserId},
                confirmed_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{reportId}
              AND status = 'DRAFT' AND deleted_at IS NULL
            """)
    int confirm(@Param("projectId") Long projectId, @Param("reportId") Long reportId,
                @Param("actorUserId") Long actorUserId);

    @Insert("INSERT INTO daily_report_work_item (daily_report_id, work_item_id) VALUES (#{reportId}, #{itemId})")
    int linkWorkItem(@Param("reportId") Long reportId, @Param("itemId") Long itemId);

    @Insert("INSERT INTO daily_report_deployment_record (daily_report_id, deployment_record_id) VALUES (#{reportId}, #{recordId})")
    int linkDeploymentRecord(@Param("reportId") Long reportId, @Param("recordId") Long recordId);

    @Delete("DELETE FROM daily_report_work_item WHERE daily_report_id = #{reportId}")
    int deleteWorkItems(@Param("reportId") Long reportId);

    @Delete("DELETE FROM daily_report_deployment_record WHERE daily_report_id = #{reportId}")
    int deleteDeploymentRecords(@Param("reportId") Long reportId);

    @Select("""
            SELECT item.id, item.title FROM daily_report_work_item link
            JOIN work_item item ON item.id = link.work_item_id
            WHERE link.daily_report_id = #{reportId} ORDER BY item.id
            """)
    List<DailyReportLinkResponse> selectWorkItems(@Param("reportId") Long reportId);

    @Select("""
            SELECT count(*) FROM meeting_record
            WHERE project_id = #{projectId} AND id = #{recordId} AND deleted_at IS NULL
            """)
    int countMeetingRecord(
            @Param("projectId") Long projectId, @Param("recordId") Long recordId);

    @Insert("""
            INSERT INTO daily_report_meeting_record (daily_report_id, meeting_record_id)
            VALUES (#{reportId}, #{recordId})
            """)
    int linkMeetingRecord(
            @Param("reportId") Long reportId, @Param("recordId") Long recordId);

    @Delete("DELETE FROM daily_report_meeting_record WHERE daily_report_id = #{reportId}")
    int deleteMeetingRecords(@Param("reportId") Long reportId);

    @Select("""
            SELECT record.id, record.title
            FROM daily_report_meeting_record link
            JOIN meeting_record record ON record.id = link.meeting_record_id
            WHERE link.daily_report_id = #{reportId}
            ORDER BY record.occurred_at, record.id
            """)
    List<DailyReportLinkResponse> selectMeetingRecords(@Param("reportId") Long reportId);

    @Select("""
            SELECT record.id,
                   CONCAT('部署记录 #', record.id, '（', record.result, '）') AS title
            FROM daily_report_deployment_record link
            JOIN deployment_record record ON record.id = link.deployment_record_id
            WHERE link.daily_report_id = #{reportId} ORDER BY record.id
            """)
    List<DailyReportLinkResponse> selectDeploymentRecords(@Param("reportId") Long reportId);

    @Insert("""
            INSERT INTO audit_log (actor_user_id, action, resource_type, resource_id, outcome, details)
            VALUES (#{actorUserId}, #{action}, 'DAILY_REPORT', #{reportId}, 'SUCCESS',
                    jsonb_build_object('projectId', #{projectId}))
            """)
    int recordAudit(@Param("actorUserId") Long actorUserId, @Param("action") String action,
                    @Param("reportId") String reportId, @Param("projectId") Long projectId);
}
