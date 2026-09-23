package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 阶段 0 模块 3 的独立数据库迁移测试。
 *
 * <p>使用空白 PostgreSQL 验证 Flyway 可以从零完成建表和基础 RBAC 数据初始化，
 * 避免测试依赖开发机上已有的数据库状态。
 */
@SpringBootTest
@Testcontainers
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 验证迁移版本、核心表和密码字段不存在明文设计。 */
    @Test
    void migratesAnEmptyDatabaseThroughVersionNine() {
        List<String> versions = jdbcTemplate.queryForList("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank
                """, String.class);
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'app_user', 'app_role', 'app_permission', 'user_role',
                      'role_permission', 'project', 'project_member', 'audit_log',
                      'file_asset', 'file_link', 'file_upload_chunk', 'deployment_asset',
                      'server_record', 'server_credential', 'environment_fingerprint',
                      'deployment_solution', 'deployment_solution_step', 'deployment_record'
                  )
                ORDER BY table_name
                """, String.class);
        List<String> passwordColumns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'app_user'
                  AND column_name LIKE 'password%'
                ORDER BY column_name
                """, String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThat(tables).containsExactlyInAnyOrder(
                "app_user",
                "app_role",
                "app_permission",
                "user_role",
                "role_permission",
                "project",
                "project_member",
                "audit_log",
                "file_asset",
                "file_link",
                "file_upload_chunk",
                "deployment_asset",
                "server_record",
                "server_credential",
                "environment_fingerprint",
                "deployment_solution",
                "deployment_solution_step",
                "deployment_record");
        assertThat(passwordColumns).containsExactly("password_hash");
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'project'
                  AND column_name IN ('customer_name', 'tags')
                ORDER BY column_name
                """, String.class)).containsExactly("customer_name", "tags");
    }

    /** 验证基础授权、文件权限和部署资产权限合并后的最小授权矩阵没有漂移。 */
    @Test
    void seedsTheExpectedRolesPermissionsAndAssignments() {
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT code FROM app_role ORDER BY code", String.class);
        List<String> permissions = jdbcTemplate.queryForList(
                "SELECT code FROM app_permission ORDER BY code", String.class);
        Map<String, Integer> permissionCounts = jdbcTemplate.query("""
                SELECT role.code, count(mapping.permission_id)::int AS permission_count
                FROM app_role role
                LEFT JOIN role_permission mapping ON mapping.role_id = role.id
                GROUP BY role.code
                """, resultSet -> {
            java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
            while (resultSet.next()) {
                counts.put(resultSet.getString("code"), resultSet.getInt("permission_count"));
            }
            return counts;
        });

        assertThat(roles)
                .containsExactly("ADMIN", "IMPLEMENTER", "PROJECT_MANAGER", "TESTER", "VISITOR");
        assertThat(permissions).containsExactly(
                "audit:read",
                "deployment_asset:read",
                "deployment_asset:write",
                "deployment_record:read",
                "deployment_record:write",
                "deployment_solution:read",
                "deployment_solution:write",
                "environment_fingerprint:read",
                "environment_fingerprint:write",
                "file:read",
                "file:write",
                "project:create",
                "project:delete",
                "project:manage_members",
                "project:read",
                "project:update",
                "role:manage",
                "server:read",
                "server:write",
                "server_credential:manage",
                "server_credential:read",
                "user:manage");
        assertThat(permissionCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ADMIN", 22,
                "PROJECT_MANAGER", 19,
                "IMPLEMENTER", 14,
                "TESTER", 7,
                "VISITOR", 7));
    }
}
