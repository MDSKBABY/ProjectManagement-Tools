package com.company.projectmanagement.environment.mapper;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.environment.domain.EnvironmentFingerprint;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 环境指纹查询始终限定项目和未删除状态。 */
@Mapper
public interface EnvironmentFingerprintMapper {

    String COLUMNS = """
            fingerprint.id, fingerprint.project_id, fingerprint.name, fingerprint.environment,
            fingerprint.operating_system, fingerprint.os_version, fingerprint.kernel_version,
            fingerprint.architecture, fingerprint.runtime_name, fingerprint.runtime_version,
            fingerprint.database_name, fingerprint.database_version,
            fingerprint.middlewares::text AS middlewares_json, fingerprint.network_zone,
            fingerprint.tags::text AS tags_json, fingerprint.notes, fingerprint.created_by,
            fingerprint.created_at, fingerprint.updated_by, fingerprint.updated_at,
            fingerprint.deleted_at
            """;

    @Insert("""
            INSERT INTO environment_fingerprint (
                project_id, name, environment, operating_system, os_version, kernel_version,
                architecture, runtime_name, runtime_version, database_name, database_version,
                middlewares, network_zone, tags, notes, created_by, updated_by
            ) VALUES (
                #{projectId}, #{name}, #{environment}, #{operatingSystem}, #{osVersion},
                #{kernelVersion}, #{architecture}, #{runtimeName}, #{runtimeVersion},
                #{databaseName}, #{databaseVersion}, CAST(#{middlewaresJson} AS jsonb),
                #{networkZone}, CAST(#{tagsJson} AS jsonb), #{notes}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EnvironmentFingerprint fingerprint);

    @Select("""
            <script>
            SELECT count(*) FROM environment_fingerprint fingerprint
            WHERE fingerprint.project_id = #{projectId} AND fingerprint.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(fingerprint.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(fingerprint.notes, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='environment != null'>AND fingerprint.environment = #{environment}</if>
            <if test='operatingSystem != null'>AND LOWER(fingerprint.operating_system) = LOWER(#{operatingSystem})</if>
            <if test='architecture != null'>AND LOWER(fingerprint.architecture) = LOWER(#{architecture})</if>
            <if test='databaseName != null'>AND LOWER(fingerprint.database_name) = LOWER(#{databaseName})</if>
            <if test='middleware != null'>
              AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(fingerprint.middlewares) value
                          WHERE LOWER(value) = LOWER(#{middleware}))
            </if>
            <if test='tag != null'>
              AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(fingerprint.tags) value
                          WHERE LOWER(value) = LOWER(#{tag}))
            </if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("environment") DeploymentEnvironment environment,
            @Param("operatingSystem") String operatingSystem,
            @Param("architecture") String architecture,
            @Param("databaseName") String databaseName,
            @Param("middleware") String middleware,
            @Param("tag") String tag);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM environment_fingerprint fingerprint
            WHERE fingerprint.project_id = #{projectId} AND fingerprint.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(fingerprint.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(fingerprint.notes, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='environment != null'>AND fingerprint.environment = #{environment}</if>
            <if test='operatingSystem != null'>AND LOWER(fingerprint.operating_system) = LOWER(#{operatingSystem})</if>
            <if test='architecture != null'>AND LOWER(fingerprint.architecture) = LOWER(#{architecture})</if>
            <if test='databaseName != null'>AND LOWER(fingerprint.database_name) = LOWER(#{databaseName})</if>
            <if test='middleware != null'>
              AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(fingerprint.middlewares) value
                          WHERE LOWER(value) = LOWER(#{middleware}))
            </if>
            <if test='tag != null'>
              AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(fingerprint.tags) value
                          WHERE LOWER(value) = LOWER(#{tag}))
            </if>
            ORDER BY fingerprint.updated_at DESC, fingerprint.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<EnvironmentFingerprint> selectPage(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("environment") DeploymentEnvironment environment,
            @Param("operatingSystem") String operatingSystem,
            @Param("architecture") String architecture,
            @Param("databaseName") String databaseName,
            @Param("middleware") String middleware,
            @Param("tag") String tag,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM environment_fingerprint fingerprint
            WHERE fingerprint.project_id = #{projectId} AND fingerprint.id = #{fingerprintId}
              AND fingerprint.deleted_at IS NULL
            """)
    EnvironmentFingerprint selectById(
            @Param("projectId") Long projectId,
            @Param("fingerprintId") Long fingerprintId);

    @Update("""
            UPDATE environment_fingerprint
            SET name = #{name}, environment = #{environment}, operating_system = #{operatingSystem},
                os_version = #{osVersion}, kernel_version = #{kernelVersion},
                architecture = #{architecture}, runtime_name = #{runtimeName},
                runtime_version = #{runtimeVersion}, database_name = #{databaseName},
                database_version = #{databaseVersion}, middlewares = CAST(#{middlewaresJson} AS jsonb),
                network_zone = #{networkZone}, tags = CAST(#{tagsJson} AS jsonb), notes = #{notes},
                updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(EnvironmentFingerprint fingerprint);

    @Update("""
            UPDATE environment_fingerprint
            SET deleted_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{fingerprintId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("fingerprintId") Long fingerprintId,
            @Param("actorUserId") Long actorUserId);

    @Select("""
            SELECT count(*) FROM deployment_solution
            WHERE environment_fingerprint_id = #{fingerprintId} AND deleted_at IS NULL
            """)
    long countActiveSolutions(@Param("fingerprintId") Long fingerprintId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'ENVIRONMENT_FINGERPRINT', #{fingerprintId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("fingerprintId") String fingerprintId,
            @Param("projectId") Long projectId);
}
