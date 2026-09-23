package com.company.projectmanagement.audit.mapper;

import com.company.projectmanagement.audit.domain.AuditLog;
import com.company.projectmanagement.audit.domain.AuditOutcome;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 审计日志只读查询，不提供修改或删除能力。 */
@Mapper
public interface AuditLogMapper {

    String FILTERS = """
            <if test='actorId != null'>AND audit.actor_user_id = #{actorId}</if>
            <if test='action != null'>AND audit.action = #{action}</if>
            <if test='resourceType != null'>AND audit.resource_type = #{resourceType}</if>
            <if test='outcome != null'>AND audit.outcome = #{outcome}</if>
            <if test='createdFrom != null'>AND audit.created_at &gt;= #{createdFrom}</if>
            <if test='createdTo != null'>AND audit.created_at &lt;= #{createdTo}</if>
            """;

    @Select("""
            <script>
            SELECT count(*) FROM audit_log audit
            WHERE 1 = 1
            """ + FILTERS + """
            </script>
            """)
    long count(
            @Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("outcome") AuditOutcome outcome,
            @Param("createdFrom") OffsetDateTime createdFrom,
            @Param("createdTo") OffsetDateTime createdTo);

    @Select("""
            <script>
            SELECT audit.id, audit.actor_user_id AS actor_id,
                   actor.username AS actor_username,
                   actor.display_name AS actor_display_name,
                   audit.action, audit.resource_type, audit.resource_id, audit.outcome,
                   audit.ip_address::text AS ip_address, audit.user_agent, audit.request_id,
                   audit.details::text AS details_json, audit.created_at
            FROM audit_log audit
            LEFT JOIN app_user actor ON actor.id = audit.actor_user_id
            WHERE 1 = 1
            """ + FILTERS + """
            ORDER BY audit.created_at DESC, audit.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AuditLog> selectPage(
            @Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("outcome") AuditOutcome outcome,
            @Param("createdFrom") OffsetDateTime createdFrom,
            @Param("createdTo") OffsetDateTime createdTo,
            @Param("offset") int offset,
            @Param("limit") int limit);
}
