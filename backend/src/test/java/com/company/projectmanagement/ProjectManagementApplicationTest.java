package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.bootstrap.InitialAdminBootstrap;
import com.company.projectmanagement.identity.domain.AppPermission;
import com.company.projectmanagement.identity.domain.AppRole;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.company.projectmanagement.identity.mapper.AppPermissionMapper;
import com.company.projectmanagement.identity.mapper.AppRoleMapper;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.identity.security.AuthenticatedUser;
import com.company.projectmanagement.identity.security.LoginAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.bootstrap-admin.username=initial-admin",
        "app.bootstrap-admin.password=Test-only-password-123!",
        "app.bootstrap-admin.display-name=初始管理员"
})
@Testcontainers
@Transactional
@AutoConfigureMockMvc
class ProjectManagementApplicationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private AppRoleMapper appRoleMapper;

    @Autowired
    private AppPermissionMapper appPermissionMapper;

    @Autowired
    private IdentityAccessMapper identityAccessMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAuthenticationService loginAuthenticationService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InitialAdminBootstrap initialAdminBootstrap;

    @Test
    void applicationContextStartsAndCreatesInitialSchema() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'app_user', 'app_role', 'app_permission', 'user_role',
                      'role_permission', 'project', 'project_member', 'audit_log'
                  )
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "app_user",
                "app_role",
                "app_permission",
                "user_role",
                "role_permission",
                "project",
                "project_member",
                "audit_log");

        String migrationVersion = jdbcTemplate.queryForObject("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank DESC
                LIMIT 1
                """, String.class);

        Integer passwordHashColumns = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'app_user'
                  AND column_name = 'password_hash'
                """, Integer.class);
        Integer plaintextPasswordColumns = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'app_user'
                  AND column_name = 'password'
                """, Integer.class);

        assertThat(migrationVersion).isEqualTo("2");
        assertThat(passwordHashColumns).isEqualTo(1);
        assertThat(plaintextPasswordColumns).isZero();
    }

    @Test
    void seedsBaseAccessControlAndCreatesTheFirstAdministratorSecurely() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT code FROM app_role ORDER BY code", String.class))
                .containsExactly("ADMIN", "IMPLEMENTER", "PROJECT_MANAGER", "TESTER", "VISITOR");
        assertThat(jdbcTemplate.queryForList(
                "SELECT code FROM app_permission ORDER BY code", String.class))
                .containsExactly(
                        "audit:read",
                        "project:create",
                        "project:delete",
                        "project:manage_members",
                        "project:read",
                        "project:update",
                        "role:manage",
                        "user:manage");

        AppUser administrator = appUserMapper.selectActiveByUsername("INITIAL-ADMIN");

        assertThat(administrator).isNotNull();
        assertThat(administrator.getPasswordHash()).isNotEqualTo("Test-only-password-123!");
        assertThat(passwordEncoder.matches(
                "Test-only-password-123!", administrator.getPasswordHash())).isTrue();
        assertThat(identityAccessMapper.selectRoleCodesByUserId(administrator.getId()))
                .containsExactly("ADMIN");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT assigned_by IS NULL
                FROM user_role
                WHERE user_id = ?
                """, Boolean.class, administrator.getId())).isTrue();
        assertThat(identityAccessMapper.selectPermissionCodesByUserId(administrator.getId()))
                .containsExactly(
                        "audit:read",
                        "project:create",
                        "project:delete",
                        "project:manage_members",
                        "project:read",
                        "project:update",
                        "role:manage",
                        "user:manage");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_log
                WHERE actor_user_id IS NULL
                  AND action = 'INITIAL_ADMIN_CREATED'
                  AND resource_type = 'USER'
                  AND resource_id = ?
                  AND outcome = 'SUCCESS'
                """, Integer.class, administrator.getId().toString()))
                .isEqualTo(1);

        AuthenticatedUser authenticatedUser = loginAuthenticationService.authenticate(
                "initial-admin", "Test-only-password-123!");
        assertThat(authenticatedUser.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "user:manage", "project:create", "audit:read");
    }

    @Test
    void doesNotOverwriteTheAdministratorWhenUsersAlreadyExist() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("initial-admin");
        String originalPasswordHash = administrator.getPasswordHash();

        initialAdminBootstrap.run(null);

        AppUser unchangedAdministrator = appUserMapper.selectActiveByUsername("initial-admin");
        assertThat(unchangedAdministrator.getPasswordHash()).isEqualTo(originalPasswordHash);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_log
                WHERE action = 'INITIAL_ADMIN_CREATED'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void persistsUserAndLoadsAssignedRolesAndPermissions() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("alice");
        user.setPasswordHash("{bcrypt}$2a$12$test-hash-not-a-real-password");
        user.setDisplayName("爱丽丝");
        user.setEmail("alice@example.com");

        AppRole role = new AppRole();
        role.setCode("TEST_PROJECT_MANAGER");
        role.setName("项目经理");

        AppPermission permission = new AppPermission();
        permission.setCode("test:project:read");
        permission.setName("查看项目");
        permission.setResource("project");
        permission.setAction("read");

        assertThat(appUserMapper.insertSelective(user)).isEqualTo(1);
        assertThat(appRoleMapper.insertSelective(role)).isEqualTo(1);
        assertThat(appPermissionMapper.insertSelective(permission)).isEqualTo(1);
        assertThat(identityAccessMapper.assignRole(user.getId(), role.getId(), user.getId())).isEqualTo(1);
        assertThat(identityAccessMapper.grantPermission(role.getId(), permission.getId())).isEqualTo(1);

        AppUser storedUser = appUserMapper.selectActiveByUsername("ALICE");

        assertThat(storedUser).isNotNull();
        assertThat(storedUser.getId()).isEqualTo(user.getId());
        assertThat(storedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(storedUser.getCreatedAt()).isNotNull();
        assertThat(objectMapper.writeValueAsString(storedUser))
                .doesNotContain("passwordHash")
                .doesNotContain(storedUser.getPasswordHash());
        assertThat(identityAccessMapper.selectRoleCodesByUserId(user.getId()))
                .containsExactly("TEST_PROJECT_MANAGER");
        assertThat(identityAccessMapper.selectPermissionCodesByUserId(user.getId()))
                .containsExactly("test:project:read");
    }

    @Test
    void activeUserLookupDoesNotReturnDisabledUsers() {
        AppUser user = new AppUser();
        user.setUsername("disabled-user");
        user.setPasswordHash("{bcrypt}$2a$12$test-hash-not-a-real-password");
        user.setDisplayName("已停用用户");
        user.setStatus(UserStatus.DISABLED);

        assertThat(appUserMapper.insertSelective(user)).isEqualTo(1);
        assertThat(appUserMapper.selectActiveByUsername("disabled-user")).isNull();
    }

    @Test
    void authenticatesActiveUserWithRolesAndPermissions() {
        AppUser user = new AppUser();
        user.setUsername("login-user");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setDisplayName("登录用户");
        appUserMapper.insertSelective(user);

        AppRole role = new AppRole();
        role.setCode("TEST_LOGIN_MANAGER");
        role.setName("项目经理");
        appRoleMapper.insertSelective(role);

        AppPermission permission = new AppPermission();
        permission.setCode("test:login:read");
        permission.setName("查看项目");
        permission.setResource("project");
        permission.setAction("read");
        appPermissionMapper.insertSelective(permission);

        identityAccessMapper.assignRole(user.getId(), role.getId(), user.getId());
        identityAccessMapper.grantPermission(role.getId(), permission.getId());

        AuthenticatedUser authenticatedUser = loginAuthenticationService.authenticate(
                "LOGIN-USER", "correct-password");

        assertThat(authenticatedUser.getId()).isEqualTo(user.getId());
        assertThat(authenticatedUser.getDisplayName()).isEqualTo("登录用户");
        assertThat(authenticatedUser.getAuthorities())
                .extracting("authority")
                .contains("ROLE_TEST_LOGIN_MANAGER", "test:login:read");
        assertThat(authenticatedUser.getPassword()).isNull();
        assertThat(user.getPasswordHash()).matches("^\\{bcrypt}\\$2[ayb]\\$12\\$.*");
    }

    @Test
    void rejectsInvalidCredentialsWithoutRevealingAccountState() {
        AppUser user = new AppUser();
        user.setUsername("locked-user");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setDisplayName("锁定用户");
        user.setStatus(UserStatus.LOCKED);
        appUserMapper.insertSelective(user);

        assertThatThrownBy(() -> loginAuthenticationService.authenticate(
                "locked-user", "correct-password"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户名或密码错误");
        assertThatThrownBy(() -> loginAuthenticationService.authenticate(
                "missing-user", "wrong-password"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void exposesHealthCheckAndRejectsUnknownRoutes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/not-yet-implemented"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void logsInReadsCurrentUserAndLogsOutWithServerSession() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("api-user");
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        user.setDisplayName("API 用户");
        appUserMapper.insertSelective(user);

        AppRole role = new AppRole();
        role.setCode("TEST_API_MANAGER");
        role.setName("项目经理");
        appRoleMapper.insertSelective(role);

        AppPermission permission = new AppPermission();
        permission.setCode("test:api:read");
        permission.setName("查看项目");
        permission.setResource("project");
        permission.setAction("read");
        appPermissionMapper.insertSelective(permission);

        identityAccessMapper.assignRole(user.getId(), role.getId(), user.getId());
        identityAccessMapper.grantPermission(role.getId(), permission.getId());

        MvcResult initialCsrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) initialCsrfResult.getRequest().getSession(false);
        JsonNode initialCsrf = objectMapper.readTree(initialCsrfResult.getResponse().getContentAsString());
        String sessionIdBeforeLogin = session.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(initialCsrf.get("headerName").asText(), initialCsrf.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "API-USER",
                                  "password": "correct-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value("api-user"))
                .andExpect(jsonPath("$.displayName").value("API 用户"))
                .andExpect(jsonPath("$.roles[0]").value("TEST_API_MANAGER"))
                .andExpect(jsonPath("$.permissions[0]").value("test:api:read"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.roles[0]").value("TEST_API_MANAGER"));

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header(initialCsrf.get("headerName").asText(), initialCsrf.get("token").asText()))
                .andExpect(status().isForbidden());

        MvcResult refreshedCsrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode refreshedCsrf = objectMapper.readTree(refreshedCsrfResult.getResponse().getContentAsString());

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header(refreshedCsrf.get("headerName").asText(), refreshedCsrf.get("token").asText()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void requiresCsrfAndReturnsStableAuthenticationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing-user","password":"wrong-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing-user","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void providesCsrfTokenForTheFrontend() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void listsUsersWithPaginationWithoutExposingPasswordHashes() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("search-user");
        user.setPasswordHash(passwordEncoder.encode("search-password"));
        user.setDisplayName("待查询用户");
        appUserMapper.insertSelective(user);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("administrator").authorities(() -> "user:manage"))
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("keyword", "待查询"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(user.getId()))
                .andExpect(jsonPath("$.data[0].username").value("search-user"))
                .andExpect(jsonPath("$.data[0].displayName").value("待查询用户"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.pageSize").value(20))
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("ordinary-user").authorities(() -> "project:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(user("administrator").authorities(() -> "user:manage"))
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void administratorCreatesAVisitorWithAHashedPasswordAndAuditRecord() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/users")
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "  new-user  ",
                                  "initialPassword": "New-user-password-123!",
                                  "displayName": "  新用户  ",
                                  "email": "new-user@example.com",
                                  "mobile": "+86 13800000000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(jsonPath("$.displayName").value("新用户"))
                .andExpect(jsonPath("$.email").value("new-user@example.com"))
                .andExpect(jsonPath("$.mobile").value("+86 13800000000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.initialPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        long createdUserId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
        AppUser createdUser = appUserMapper.selectActiveByUsername("NEW-USER");
        AppUser administrator = appUserMapper.selectActiveByUsername("initial-admin");

        assertThat(createdUser.getId()).isEqualTo(createdUserId);
        assertThat(createdUser.getPasswordHash()).isNotEqualTo("New-user-password-123!");
        assertThat(passwordEncoder.matches(
                "New-user-password-123!", createdUser.getPasswordHash())).isTrue();
        assertThat(identityAccessMapper.selectRoleCodesByUserId(createdUserId))
                .containsExactly("VISITOR");
        assertThat(identityAccessMapper.countSuccessfulUserAudit(
                administrator.getId(),
                "USER_CREATED",
                Long.toString(createdUserId),
                "new-user"))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateUsernameAndInvalidInitialPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "INITIAL-ADMIN",
                                  "initialPassword": "Another-password-123!",
                                  "displayName": "重复账号"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USERNAME_EXISTS"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "weak-password-user",
                                  "initialPassword": "short",
                                  "displayName": "弱密码用户"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void administratorDisablesAUserAndWritesAnAuditRecord() throws Exception {
        AppUser target = new AppUser();
        target.setUsername("status-target");
        target.setPasswordHash(passwordEncoder.encode("status-password"));
        target.setDisplayName("状态目标用户");
        appUserMapper.insertSelective(target);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", target.getId())
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId()))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser disabled = appUserMapper.selectForAdministrationById(target.getId());
        AppUser administrator = appUserMapper.selectActiveByUsername("initial-admin");
        assertThat(disabled.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(identityAccessMapper.countSuccessfulUserAudit(
                administrator.getId(),
                "USER_STATUS_CHANGED",
                target.getId().toString(),
                "status-target"))
                .isEqualTo(1);
    }

    @Test
    void rejectsSelfDisableInvalidStatusAndMissingUser() throws Exception {
        AppUser administrator = appUserMapper.selectActiveByUsername("initial-admin");

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", administrator.getId())
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SELF_DISABLE_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", administrator.getId())
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", 999999L)
                        .with(user("initial-admin").authorities(() -> "user:manage"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }
}
