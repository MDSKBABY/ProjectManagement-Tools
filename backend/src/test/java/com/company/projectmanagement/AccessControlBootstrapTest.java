package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.projectmanagement.identity.bootstrap.InitialAdminBootstrap;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.mapper.AppRoleMapper;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.identity.security.AuthenticatedUser;
import com.company.projectmanagement.identity.security.DatabaseUserDetailsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 独立验证基础 RBAC 与首位管理员初始化的安全边界。
 *
 * <p>每个测试都在事务中运行并回滚，避免状态修改影响其他模块的测试结果。
 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=bootstrap-admin",
        "app.bootstrap-admin.password=Test-only-bootstrap-password-123!",
        "app.bootstrap-admin.display-name=初始化测试管理员"
})
@Testcontainers
@Transactional
class AccessControlBootstrapTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private AppRoleMapper appRoleMapper;

    @Autowired
    private IdentityAccessMapper identityAccessMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private InitialAdminBootstrap initialAdminBootstrap;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void initializesTheFirstAdministratorWithHashedPasswordPermissionsAndAudit() {
        AppUser administrator = appUserMapper.selectActiveByUsername("BOOTSTRAP-ADMIN");

        assertThat(administrator).isNotNull();
        assertThat(administrator.getDisplayName()).isEqualTo("初始化测试管理员");
        assertThat(administrator.getPasswordHash())
                .isNotEqualTo("Test-only-bootstrap-password-123!")
                .matches("^\\{bcrypt}\\$2[ayb]\\$12\\$.*");
        assertThat(passwordEncoder.matches(
                "Test-only-bootstrap-password-123!", administrator.getPasswordHash())).isTrue();

        AuthenticatedUser loadedUser = (AuthenticatedUser) userDetailsService
                .loadUserByUsername("bootstrap-admin");
        assertThat(loadedUser.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder(
                        "ROLE_ADMIN",
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
                        "user:manage",
                        "work_item:delete",
                        "work_item:read",
                        "work_item:write");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT assigned_by IS NULL
                FROM user_role
                WHERE user_id = ?
                """, Boolean.class, administrator.getId())).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_log
                WHERE actor_user_id IS NULL
                  AND action = 'INITIAL_ADMIN_CREATED'
                  AND resource_type = 'USER'
                  AND resource_id = ?
                  AND outcome = 'SUCCESS'
                """, Integer.class, administrator.getId().toString())).isEqualTo(1);
    }

    @Test
    void skipsRepeatedInitializationWithoutChangingCredentialsOrRoles() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("bootstrap-admin");
        String originalPasswordHash = administrator.getPasswordHash();
        List<String> originalRoles = identityAccessMapper
                .selectRoleCodesByUserId(administrator.getId());

        initialAdminBootstrap.run(null);

        AppUser unchanged = appUserMapper.selectActiveByUsername("bootstrap-admin");
        assertThat(unchanged.getPasswordHash()).isEqualTo(originalPasswordHash);
        assertThat(identityAccessMapper.selectRoleCodesByUserId(unchanged.getId()))
                .containsExactlyElementsOf(originalRoles);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_log
                WHERE action = 'INITIAL_ADMIN_CREATED'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void skipsWhenConfigurationIsAbsentAndRejectsPartialConfiguration() throws Exception {
        long originalUserCount = appUserMapper.countAllUsers();

        // 两项均未配置代表管理员引导未启用，应当安全跳过。
        newBootstrap("", "").run(null);
        assertThat(appUserMapper.countAllUsers()).isEqualTo(originalUserCount);

        // 只配置一项通常意味着部署变量遗漏，必须直接失败而不能创建不完整账号。
        assertThatThrownBy(() -> newBootstrap("partial-admin", "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INITIAL_ADMIN_USERNAME 和 INITIAL_ADMIN_PASSWORD 必须同时配置");
        assertThatThrownBy(() -> newBootstrap("", "partial-password").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INITIAL_ADMIN_USERNAME 和 INITIAL_ADMIN_PASSWORD 必须同时配置");
        assertThat(appUserMapper.countAllUsers()).isEqualTo(originalUserCount);
    }

    @Test
    void excludesNonActiveUsersFromTheAuthenticationLookup() {
        AppUser administrator = appUserMapper.selectActiveByUsername("bootstrap-admin");
        assertThat(appUserMapper.updateStatusForAdministration(
                administrator.getId(), UserStatus.DISABLED)).isEqualTo(1);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("bootstrap-admin"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User was not found");
    }

    /** 创建不同配置的初始化器，用于验证部署变量边界而不重启应用上下文。 */
    private InitialAdminBootstrap newBootstrap(String username, String password) {
        return new InitialAdminBootstrap(
                username,
                password,
                "测试管理员",
                appUserMapper,
                appRoleMapper,
                identityAccessMapper,
                passwordEncoder);
    }
}
