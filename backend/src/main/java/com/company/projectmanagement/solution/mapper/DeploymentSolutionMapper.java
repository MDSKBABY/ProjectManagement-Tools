package com.company.projectmanagement.solution.mapper;

import com.company.projectmanagement.solution.domain.DeploymentSolution;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStatus;
import com.company.projectmanagement.solution.domain.DeploymentSolutionStep;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 方案查询限制项目，子步骤只返回精确关联的资产版本。 */
@Mapper
public interface DeploymentSolutionMapper {

    String COLUMNS = """
            solution.id, solution.project_id, solution.environment_fingerprint_id,
            fingerprint.name AS fingerprint_name, solution.name, solution.scenario,
            solution.architecture_description, solution.prerequisites, solution.rollback_steps,
            solution.risk_notes, solution.status, solution.created_by, solution.created_at,
            solution.updated_by, solution.updated_at, solution.deleted_at,
            (SELECT count(*)::int FROM deployment_solution_step step
             WHERE step.solution_id = solution.id) AS step_count
            """;

    @Insert("""
            INSERT INTO deployment_solution (
                project_id, environment_fingerprint_id, name, scenario,
                architecture_description, prerequisites, rollback_steps, risk_notes,
                status, created_by, updated_by
            ) VALUES (
                #{projectId}, #{environmentFingerprintId}, #{name}, #{scenario},
                #{architectureDescription}, #{prerequisites}, #{rollbackSteps}, #{riskNotes},
                #{status}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeploymentSolution solution);

    @Insert("""
            INSERT INTO deployment_solution_step (
                solution_id, step_order, title, instructions, deployment_asset_id,
                parameters_template
            ) VALUES (
                #{solutionId}, #{stepOrder}, #{title}, #{instructions}, #{deploymentAssetId},
                #{parametersTemplate}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertStep(DeploymentSolutionStep step);

    @Select("""
            <script>
            SELECT count(*) FROM deployment_solution solution
            WHERE solution.project_id = #{projectId} AND solution.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(solution.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(solution.scenario) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>AND solution.status = #{status}</if>
            <if test='fingerprintId != null'>
              AND solution.environment_fingerprint_id = #{fingerprintId}
            </if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") DeploymentSolutionStatus status,
            @Param("fingerprintId") Long fingerprintId);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM deployment_solution solution
            JOIN environment_fingerprint fingerprint
              ON fingerprint.id = solution.environment_fingerprint_id
            WHERE solution.project_id = #{projectId} AND solution.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(solution.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(solution.scenario) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>AND solution.status = #{status}</if>
            <if test='fingerprintId != null'>
              AND solution.environment_fingerprint_id = #{fingerprintId}
            </if>
            ORDER BY solution.updated_at DESC, solution.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DeploymentSolution> selectPage(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") DeploymentSolutionStatus status,
            @Param("fingerprintId") Long fingerprintId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM deployment_solution solution
            JOIN environment_fingerprint fingerprint
              ON fingerprint.id = solution.environment_fingerprint_id
            WHERE solution.project_id = #{projectId} AND solution.id = #{solutionId}
              AND solution.deleted_at IS NULL
            """)
    DeploymentSolution selectById(
            @Param("projectId") Long projectId, @Param("solutionId") Long solutionId);

    @Select("""
            SELECT step.id, step.solution_id, step.step_order, step.title, step.instructions,
                   step.deployment_asset_id, step.parameters_template,
                   asset.name AS asset_name, asset.version AS asset_version,
                   asset.version_label AS asset_version_label
            FROM deployment_solution_step step
            JOIN deployment_asset asset ON asset.id = step.deployment_asset_id
            WHERE step.solution_id = #{solutionId}
            ORDER BY step.step_order, step.id
            """)
    List<DeploymentSolutionStep> selectSteps(@Param("solutionId") Long solutionId);

    @Update("""
            UPDATE deployment_solution
            SET environment_fingerprint_id = #{environmentFingerprintId}, name = #{name},
                scenario = #{scenario}, architecture_description = #{architectureDescription},
                prerequisites = #{prerequisites}, rollback_steps = #{rollbackSteps},
                risk_notes = #{riskNotes}, status = #{status}, updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(DeploymentSolution solution);

    @Delete("DELETE FROM deployment_solution_step WHERE solution_id = #{solutionId}")
    int deleteSteps(@Param("solutionId") Long solutionId);

    @Update("""
            UPDATE deployment_solution
            SET deleted_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{solutionId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("solutionId") Long solutionId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'DEPLOYMENT_SOLUTION', #{solutionId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("solutionId") String solutionId,
            @Param("projectId") Long projectId);
}
