package com.company.projectmanagement.record.mapper;

import com.company.projectmanagement.record.domain.DeploymentRecord;
import com.company.projectmanagement.record.domain.DeploymentResult;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 部署执行内容只追加，唯一可变字段是独立管理的成功基线标记。 */
@Mapper
public interface DeploymentRecordMapper {

    String COLUMNS = """
            record.id, record.project_id, record.server_id,
            (record.server_snapshot ->> 'name') AS server_name,
            record.solution_id, (record.solution_snapshot ->> 'name') AS solution_name,
            record.environment_fingerprint_id, record.result, record.executed_by,
            executor.display_name AS executed_by_name, record.executed_at,
            record.exception_notes, record.notes, record.is_baseline AS baseline,
            record.server_snapshot::text AS server_snapshot_json,
            record.environment_snapshot::text AS environment_snapshot_json,
            record.solution_snapshot::text AS solution_snapshot_json,
            record.created_by, record.created_at
            """;

    @Insert("""
            INSERT INTO deployment_record (
                project_id, server_id, solution_id, environment_fingerprint_id,
                result, executed_by, executed_at, exception_notes, notes, is_baseline,
                server_snapshot, environment_snapshot, solution_snapshot, created_by
            ) VALUES (
                #{projectId}, #{serverId}, #{solutionId}, #{environmentFingerprintId},
                #{result}, #{executedBy}, #{executedAt}, #{exceptionNotes}, #{notes}, FALSE,
                CAST(#{serverSnapshotJson} AS jsonb),
                CAST(#{environmentSnapshotJson} AS jsonb),
                CAST(#{solutionSnapshotJson} AS jsonb), #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeploymentRecord record);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM deployment_record record
            JOIN app_user executor ON executor.id = record.executed_by
            WHERE record.project_id = #{projectId} AND record.id = #{recordId}
            """)
    DeploymentRecord selectById(
            @Param("projectId") Long projectId, @Param("recordId") Long recordId);

    @Select("""
            <script>
            SELECT count(*) FROM deployment_record record
            WHERE record.project_id = #{projectId}
            <if test='result != null'>AND record.result = #{result}</if>
            <if test='serverId != null'>AND record.server_id = #{serverId}</if>
            <if test='baseline != null'>AND record.is_baseline = #{baseline}</if>
            <if test='executedFrom != null'>AND record.executed_at &gt;= #{executedFrom}</if>
            <if test='executedTo != null'>AND record.executed_at &lt;= #{executedTo}</if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("result") DeploymentResult result,
            @Param("serverId") Long serverId,
            @Param("baseline") Boolean baseline,
            @Param("executedFrom") OffsetDateTime executedFrom,
            @Param("executedTo") OffsetDateTime executedTo);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM deployment_record record
            JOIN app_user executor ON executor.id = record.executed_by
            WHERE record.project_id = #{projectId}
            <if test='result != null'>AND record.result = #{result}</if>
            <if test='serverId != null'>AND record.server_id = #{serverId}</if>
            <if test='baseline != null'>AND record.is_baseline = #{baseline}</if>
            <if test='executedFrom != null'>AND record.executed_at &gt;= #{executedFrom}</if>
            <if test='executedTo != null'>AND record.executed_at &lt;= #{executedTo}</if>
            ORDER BY record.executed_at DESC, record.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DeploymentRecord> selectPage(
            @Param("projectId") Long projectId,
            @Param("result") DeploymentResult result,
            @Param("serverId") Long serverId,
            @Param("baseline") Boolean baseline,
            @Param("executedFrom") OffsetDateTime executedFrom,
            @Param("executedTo") OffsetDateTime executedTo,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM deployment_record record
            JOIN app_user executor ON executor.id = record.executed_by
            WHERE record.project_id = #{projectId}
              AND record.result = 'SUCCESS' AND record.is_baseline = TRUE
            ORDER BY record.executed_at DESC, record.id DESC
            LIMIT #{limit}
            """)
    List<DeploymentRecord> selectBaselineCandidates(
            @Param("projectId") Long projectId, @Param("limit") int limit);

    @Update("""
            UPDATE deployment_record SET is_baseline = #{baseline}
            WHERE project_id = #{projectId} AND id = #{recordId}
            """)
    int updateBaseline(
            @Param("projectId") Long projectId,
            @Param("recordId") Long recordId,
            @Param("baseline") boolean baseline);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'DEPLOYMENT_RECORD', #{recordId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("recordId") String recordId,
            @Param("projectId") Long projectId);
}
