package com.company.projectmanagement.project.mapper;

import com.company.projectmanagement.project.domain.ProjectMember;
import com.company.projectmanagement.project.domain.ProjectMemberCandidate;
import com.company.projectmanagement.project.domain.ProjectMemberRole;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 项目成员持久层，所有动态条件均通过 MyBatis 参数绑定。 */
@Mapper
public interface ProjectMemberMapper {

    @Select("""
            <script>
            SELECT count(*)
            FROM app_user user_record
            WHERE user_record.status = 'ACTIVE' AND user_record.deleted_at IS NULL
              AND NOT EXISTS (
                    SELECT 1 FROM project_member member
                    WHERE member.project_id = #{projectId} AND member.user_id = user_record.id
              )
            <if test='keyword != null'>
              AND (LOWER(user_record.username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(user_record.display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            </script>
            """)
    long countCandidates(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT user_record.id AS user_id, user_record.username, user_record.display_name
            FROM app_user user_record
            WHERE user_record.status = 'ACTIVE' AND user_record.deleted_at IS NULL
              AND NOT EXISTS (
                    SELECT 1 FROM project_member member
                    WHERE member.project_id = #{projectId} AND member.user_id = user_record.id
              )
            <if test='keyword != null'>
              AND (LOWER(user_record.username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(user_record.display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            ORDER BY user_record.display_name, user_record.id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProjectMemberCandidate> selectCandidates(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT count(*)
            FROM project_member member
            JOIN app_user user_record ON user_record.id = member.user_id
            WHERE member.project_id = #{projectId}
            <if test='keyword != null'>
              AND (LOWER(user_record.username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(user_record.display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            </script>
            """)
    long countByProject(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT member.project_id, member.user_id, user_record.username,
                   user_record.display_name, user_record.status AS user_status,
                   member.project_role, member.joined_at
            FROM project_member member
            JOIN app_user user_record ON user_record.id = member.user_id
            WHERE member.project_id = #{projectId}
            <if test='keyword != null'>
              AND (LOWER(user_record.username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(user_record.display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            ORDER BY CASE member.project_role
                       WHEN 'OWNER' THEN 1 WHEN 'MANAGER' THEN 2
                       WHEN 'MEMBER' THEN 3 ELSE 4 END,
                     member.joined_at, member.user_id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProjectMember> selectByProject(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT member.project_id, member.user_id, user_record.username,
                   user_record.display_name, user_record.status AS user_status,
                   member.project_role, member.joined_at
            FROM project_member member
            JOIN app_user user_record ON user_record.id = member.user_id
            WHERE member.project_id = #{projectId} AND member.user_id = #{userId}
            """)
    ProjectMember selectOne(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Insert("""
            INSERT INTO project_member (project_id, user_id, project_role, created_by)
            VALUES (#{projectId}, #{userId}, #{role}, #{createdBy})
            """)
    int insert(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("role") ProjectMemberRole role,
            @Param("createdBy") Long createdBy);

    @Update("""
            UPDATE project_member
            SET project_role = #{role}
            WHERE project_id = #{projectId} AND user_id = #{userId}
              AND project_role <> 'OWNER'
            """)
    int updateRole(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("role") ProjectMemberRole role);

    @Delete("""
            DELETE FROM project_member
            WHERE project_id = #{projectId} AND user_id = #{userId}
              AND project_role <> 'OWNER'
            """)
    int deleteNonOwner(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'PROJECT', #{projectId}, 'SUCCESS',
                jsonb_build_object(
                    'memberUserId', #{memberUserId},
                    'previousRole', CAST(#{previousRole} AS text),
                    'newRole', CAST(#{newRole} AS text)
                )
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("projectId") String projectId,
            @Param("memberUserId") Long memberUserId,
            @Param("previousRole") String previousRole,
            @Param("newRole") String newRole);
}
