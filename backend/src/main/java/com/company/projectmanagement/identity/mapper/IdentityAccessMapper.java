package com.company.projectmanagement.identity.mapper;

import com.company.projectmanagement.identity.domain.UserStatus;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdentityAccessMapper {

    @Insert("""
            INSERT INTO audit_log (action, resource_type, resource_id, outcome)
            VALUES (#{action}, #{resourceType}, #{resourceId}, 'SUCCESS')
            """)
    int recordSuccessfulSystemAudit(
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, 'USER_CREATED', 'USER', #{resourceId}, 'SUCCESS',
                jsonb_build_object('username', #{username})
            )
            """)
    int recordUserCreatedAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("resourceId") String resourceId,
            @Param("username") String username);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, 'USER_STATUS_CHANGED', 'USER', #{resourceId}, 'SUCCESS',
                jsonb_build_object(
                    'username', #{username},
                    'previousStatus', #{previousStatus},
                    'newStatus', #{newStatus}
                )
            )
            """)
    int recordUserStatusChangedAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("resourceId") String resourceId,
            @Param("username") String username,
            @Param("previousStatus") UserStatus previousStatus,
            @Param("newStatus") UserStatus newStatus);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, 'PASSWORD_CHANGED', 'USER', #{resourceId}, 'SUCCESS',
                jsonb_build_object('username', #{username})
            )
            """)
    int recordPasswordChangedAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("resourceId") String resourceId,
            @Param("username") String username);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, 'PASSWORD_RESET', 'USER', #{resourceId}, 'SUCCESS',
                jsonb_build_object('username', #{username})
            )
            """)
    int recordPasswordResetAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("resourceId") String resourceId,
            @Param("username") String username);

    @Select("""
            SELECT count(*)
            FROM audit_log
            WHERE actor_user_id = #{actorUserId}
              AND action = #{action}
              AND resource_type = 'USER'
              AND resource_id = #{resourceId}
              AND outcome = 'SUCCESS'
              AND details ->> 'username' = #{username}
            """)
    int countSuccessfulUserAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("resourceId") String resourceId,
            @Param("username") String username);

    @Insert("""
            INSERT INTO user_role (user_id, role_id, assigned_by)
            VALUES (#{userId}, #{roleId}, #{assignedBy})
            ON CONFLICT (user_id, role_id) DO NOTHING
            """)
    int assignRole(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("assignedBy") Long assignedBy);

    @Insert("""
            INSERT INTO role_permission (role_id, permission_id)
            VALUES (#{roleId}, #{permissionId})
            ON CONFLICT (role_id, permission_id) DO NOTHING
            """)
    int grantPermission(
            @Param("roleId") Long roleId,
            @Param("permissionId") Long permissionId);

    @Select("""
            SELECT DISTINCT role.code
            FROM app_role role
            JOIN user_role mapping ON mapping.role_id = role.id
            WHERE mapping.user_id = #{userId}
              AND role.deleted_at IS NULL
            ORDER BY role.code
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT permission.code
            FROM app_permission permission
            JOIN role_permission permission_mapping
              ON permission_mapping.permission_id = permission.id
            JOIN user_role role_mapping
              ON role_mapping.role_id = permission_mapping.role_id
            JOIN app_role role ON role.id = role_mapping.role_id
            WHERE role_mapping.user_id = #{userId}
              AND role.deleted_at IS NULL
            ORDER BY permission.code
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
