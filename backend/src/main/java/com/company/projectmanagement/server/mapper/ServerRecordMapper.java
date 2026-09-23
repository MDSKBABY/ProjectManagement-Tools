package com.company.projectmanagement.server.mapper;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.server.domain.ServerCredential;
import com.company.projectmanagement.server.domain.ServerRecord;
import com.company.projectmanagement.server.domain.ServerStatus;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 服务器查询始终带项目与软删除条件；凭据 SQL 只返回给受控服务方法。 */
@Mapper
public interface ServerRecordMapper {

    String RESPONSE_COLUMNS = """
            server.id, server.project_id, server.name, server.host, server.port,
            server.environment, server.status, server.operating_system, server.architecture,
            server.purpose, server.description, server.created_by, server.created_at,
            server.updated_by, server.updated_at, server.deleted_at,
            EXISTS (SELECT 1 FROM server_credential credential WHERE credential.server_id = server.id)
                AS credential_configured
            """;

    @Insert("""
            INSERT INTO server_record (
                project_id, name, host, port, environment, status, operating_system,
                architecture, purpose, description, created_by, updated_by
            ) VALUES (
                #{projectId}, #{name}, #{host}, #{port}, #{environment}, #{status},
                #{operatingSystem}, #{architecture}, #{purpose}, #{description},
                #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ServerRecord server);

    @Select("""
            <script>
            SELECT count(*) FROM server_record server
            WHERE server.project_id = #{projectId} AND server.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(server.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(server.host) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(server.purpose, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='environment != null'>AND server.environment = #{environment}</if>
            <if test='status != null'>AND server.status = #{status}</if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("environment") DeploymentEnvironment environment,
            @Param("status") ServerStatus status);

    @Select("""
            <script>
            SELECT
            """ + RESPONSE_COLUMNS + """
            FROM server_record server
            WHERE server.project_id = #{projectId} AND server.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(server.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(server.host) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(server.purpose, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='environment != null'>AND server.environment = #{environment}</if>
            <if test='status != null'>AND server.status = #{status}</if>
            ORDER BY server.updated_at DESC, server.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ServerRecord> selectPage(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("environment") DeploymentEnvironment environment,
            @Param("status") ServerStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + RESPONSE_COLUMNS + """
            FROM server_record server
            WHERE server.project_id = #{projectId} AND server.id = #{serverId}
              AND server.deleted_at IS NULL
            """)
    ServerRecord selectById(
            @Param("projectId") Long projectId,
            @Param("serverId") Long serverId);

    @Update("""
            UPDATE server_record
            SET name = #{name}, host = #{host}, port = #{port}, environment = #{environment},
                status = #{status}, operating_system = #{operatingSystem},
                architecture = #{architecture}, purpose = #{purpose}, description = #{description},
                updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int update(ServerRecord server);

    @Update("""
            UPDATE server_record
            SET deleted_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{serverId} AND project_id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("serverId") Long serverId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO server_credential (
                server_id, ciphertext, nonce, key_version, updated_by
            ) VALUES (
                #{serverId}, #{ciphertext}, #{nonce}, #{keyVersion}, #{updatedBy}
            )
            ON CONFLICT (server_id) DO UPDATE
            SET ciphertext = EXCLUDED.ciphertext,
                nonce = EXCLUDED.nonce,
                key_version = EXCLUDED.key_version,
                updated_by = EXCLUDED.updated_by,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertCredential(ServerCredential credential);

    @Select("""
            SELECT server_id, ciphertext, nonce, key_version, updated_by
            FROM server_credential
            WHERE server_id = #{serverId}
            """)
    ServerCredential selectCredential(@Param("serverId") Long serverId);

    @Delete("DELETE FROM server_credential WHERE server_id = #{serverId}")
    int deleteCredential(@Param("serverId") Long serverId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, #{resourceType}, #{serverId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("serverId") String serverId,
            @Param("projectId") Long projectId);
}
