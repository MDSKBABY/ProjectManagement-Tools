package com.company.projectmanagement.project.mapper;

import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.domain.ProjectStatus;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 项目查询统一在 SQL 层限制可见范围，避免分页后再过滤造成越权或数量错误。 */
@Mapper
public interface ProjectMapper {

    String PROJECT_COLUMNS = """
            project.id, project.code, project.name, project.customer_name,
            project.description, project.status, project.owner_id,
            owner.display_name AS owner_display_name,
            project.start_date, project.end_date, project.tags::text AS tags_json,
            project.created_by, project.created_at, project.updated_by,
            project.updated_at, project.deleted_at
            """;

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM project
                WHERE LOWER(code) = LOWER(#{code}) AND deleted_at IS NULL
            )
            """)
    boolean existsActiveCode(@Param("code") String code);

    @Insert("""
            INSERT INTO project (
                code, name, customer_name, description, status, owner_id,
                start_date, end_date, tags, created_by
            ) VALUES (
                #{code}, #{name}, #{customerName}, #{description}, #{status}, #{ownerId},
                #{startDate}, #{endDate}, CAST(#{tagsJson} AS jsonb), #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Project project);

    @Insert("""
            INSERT INTO project_member (project_id, user_id, project_role, created_by)
            VALUES (#{projectId}, #{userId}, 'OWNER', #{createdBy})
            """)
    int insertOwnerMember(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("createdBy") Long createdBy);

    @Select("""
            <script>
            SELECT count(*)
            FROM project
            WHERE deleted_at IS NULL
              AND (#{administrator} OR EXISTS (
                    SELECT 1 FROM project_member member
                    WHERE member.project_id = project.id AND member.user_id = #{actorUserId}
              ))
            <if test='keyword != null'>
              AND (LOWER(code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(customer_name, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>
              AND status = #{status}
            </if>
            </script>
            """)
    long countVisible(
            @Param("actorUserId") Long actorUserId,
            @Param("administrator") boolean administrator,
            @Param("keyword") String keyword,
            @Param("status") ProjectStatus status);

    @Select("""
            <script>
            SELECT
            """ + PROJECT_COLUMNS + """
            FROM project
            JOIN app_user owner ON owner.id = project.owner_id
            WHERE project.deleted_at IS NULL
              AND (#{administrator} OR EXISTS (
                    SELECT 1 FROM project_member member
                    WHERE member.project_id = project.id AND member.user_id = #{actorUserId}
              ))
            <if test='keyword != null'>
              AND (LOWER(project.code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(project.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(project.customer_name, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>
              AND project.status = #{status}
            </if>
            ORDER BY project.updated_at DESC, project.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Project> selectVisible(
            @Param("actorUserId") Long actorUserId,
            @Param("administrator") boolean administrator,
            @Param("keyword") String keyword,
            @Param("status") ProjectStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + PROJECT_COLUMNS + """
            FROM project
            JOIN app_user owner ON owner.id = project.owner_id
            WHERE project.id = #{projectId}
              AND project.deleted_at IS NULL
              AND (#{administrator} OR EXISTS (
                    SELECT 1 FROM project_member member
                    WHERE member.project_id = project.id AND member.user_id = #{actorUserId}
              ))
            """)
    Project selectVisibleById(
            @Param("projectId") Long projectId,
            @Param("actorUserId") Long actorUserId,
            @Param("administrator") boolean administrator);

    @Select("""
            SELECT
            """ + PROJECT_COLUMNS + """
            FROM project
            JOIN app_user owner ON owner.id = project.owner_id
            WHERE project.id = #{projectId}
            """)
    Project selectByIdIncludingDeleted(@Param("projectId") Long projectId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM project
                WHERE id = #{projectId} AND owner_id = #{userId}
                UNION ALL
                SELECT 1 FROM project_member
                WHERE project_id = #{projectId}
                  AND user_id = #{userId}
                  AND project_role IN ('OWNER', 'MANAGER')
            )
            """)
    boolean canManage(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM project
                WHERE id = #{projectId} AND deleted_at IS NULL AND owner_id = #{userId}
                UNION ALL
                SELECT 1
                FROM project_member member
                JOIN project ON project.id = member.project_id
                WHERE member.project_id = #{projectId}
                  AND member.user_id = #{userId}
                  AND member.project_role IN ('OWNER', 'MANAGER', 'MEMBER')
                  AND project.deleted_at IS NULL
            )
            """)
    boolean canWriteFiles(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Update("""
            UPDATE project
            SET name = #{name}, customer_name = #{customerName}, description = #{description},
                status = #{status}, start_date = #{startDate}, end_date = #{endDate},
                tags = CAST(#{tagsJson} AS jsonb), updated_by = #{updatedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int update(Project project);

    @Update("""
            UPDATE project
            SET deleted_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{projectId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'PROJECT', #{projectId}, 'SUCCESS',
                jsonb_build_object('code', #{code})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("projectId") String projectId,
            @Param("code") String code);
}
