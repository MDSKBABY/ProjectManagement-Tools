package com.company.projectmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证日报草稿、关联校验、确认锁定和 AI 失败降级。 */
@SpringBootTest(properties = {
        "app.bootstrap-admin.username=daily-admin",
        "app.bootstrap-admin.password=Test-only-daily-password-123!",
        "app.ollama.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DailyReportApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void cleanTestData() {
        jdbcTemplate.update("DELETE FROM daily_report_deployment_record");
        jdbcTemplate.update("DELETE FROM daily_report_meeting_record");
        jdbcTemplate.update("DELETE FROM daily_report_work_item");
        jdbcTemplate.update("DELETE FROM daily_report");
        jdbcTemplate.update("DELETE FROM meeting_record");
        jdbcTemplate.update("DELETE FROM deployment_record");
        jdbcTemplate.update("DELETE FROM work_item_status_log");
        jdbcTemplate.update("DELETE FROM work_item");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'daily-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'DAILY_REPORT'");
    }

    @Test
    void createsListsAndConfirmsReportThenPreventsOverwrite() throws Exception {
        AppUser owner = insertUser("daily-owner", "日报负责人");
        long projectId = insertProject(owner, "DAILY-ONE");
        long workItemId = insertWorkItem(projectId, owner);
        long meetingRecordId = insertMeetingRecord(projectId, owner);

        JsonNode created = create(projectId, owner,
                request("完成登录联调", "7.5", workItemId, meetingRecordId));
        long reportId = created.get("id").asLong();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("workItems").get(0).get("id").asLong()).isEqualTo(workItemId);
        assertThat(created.get("meetingRecords").get(0).get("id").asLong()).isEqualTo(meetingRecordId);

        mockMvc.perform(get("/api/v1/projects/{projectId}/daily-reports", projectId)
                        .param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().plusDays(1).toString())
                        .with(actor(owner.getUsername(), "daily_report:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/projects/{projectId}/daily-reports/{reportId}/confirm",
                                projectId, reportId)
                        .with(actor(owner.getUsername(), "daily_report:confirm"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/v1/projects/{projectId}/daily-reports/{reportId}",
                                projectId, reportId)
                        .with(actor(owner.getUsername(), "daily_report:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("试图覆盖", "8", workItemId, meetingRecordId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DAILY_REPORT_LOCKED"));
    }

    @Test
    void keepsOriginalContentWhenOllamaIsUnavailable() throws Exception {
        AppUser member = insertUser("daily-member", "日报成员");
        long projectId = insertProject(member, "DAILY-AI");
        JsonNode report = create(projectId, member, request("原始工作内容", "4", null, null));
        long reportId = report.get("id").asLong();

        mockMvc.perform(post("/api/v1/projects/{projectId}/daily-reports/{reportId}/polish",
                                projectId, reportId)
                        .with(actor(member.getUsername(), "daily_report:write"))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("OLLAMA_UNAVAILABLE"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT original_content FROM daily_report WHERE id = ?", String.class, reportId))
                .isEqualTo("原始工作内容");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT polished_content IS NULL FROM daily_report WHERE id = ?", Boolean.class, reportId))
                .isTrue();
    }

    @Test
    void rejectsWorkItemsFromAnotherProject() throws Exception {
        AppUser owner = insertUser("daily-cross-owner", "日报跨项目负责人");
        long projectId = insertProject(owner, "DAILY-HOME");
        long otherProjectId = insertProject(owner, "DAILY-OTHER");
        long foreignWorkItemId = insertWorkItem(otherProjectId, owner);

        mockMvc.perform(post("/api/v1/projects/{projectId}/daily-reports", projectId)
                        .with(actor(owner.getUsername(), "daily_report:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("跨项目关联", "2", foreignWorkItemId, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_NOT_FOUND"));
    }

    @Test
    void rejectsMeetingRecordsFromAnotherProject() throws Exception {
        AppUser owner = insertUser("daily-meeting-owner", "日报会议负责人");
        long projectId = insertProject(owner, "DAILY-MEETING-HOME");
        long otherProjectId = insertProject(owner, "DAILY-MEETING-OTHER");
        long foreignMeetingId = insertMeetingRecord(otherProjectId, owner);

        mockMvc.perform(post("/api/v1/projects/{projectId}/daily-reports", projectId)
                        .with(actor(owner.getUsername(), "daily_report:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("跨项目关联会议", "2", null, foreignMeetingId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEETING_RECORD_NOT_FOUND"));
    }

    @Test
    void rejectsDuplicateReportForSameReporterAndDateWithFriendlyConflict() throws Exception {
        AppUser owner = insertUser("daily-duplicate-owner", "重复日报负责人");
        long projectId = insertProject(owner, "DAILY-DUPLICATE");
        create(projectId, owner, request("第一份日报", "8", null, null));

        mockMvc.perform(post("/api/v1/projects/{projectId}/daily-reports", projectId)
                        .with(actor(owner.getUsername(), "daily_report:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("重复日报", "8", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DAILY_REPORT_ALREADY_EXISTS"));
    }

    private JsonNode create(long projectId, AppUser actor, String request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/daily-reports", projectId)
                        .with(actor(actor.getUsername(), "daily_report:write"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private AppUser insertUser(String username, String displayName) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, username,
                "{bcrypt}$2a$12$test-hash-not-used-by-mock-authentication", displayName);
        AppUser user = new AppUser();
        user.setId(id); user.setUsername(username); user.setDisplayName(displayName);
        return user;
    }

    private long insertProject(AppUser owner, String code) {
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES (?, ?, 'PLANNING', ?, ?) RETURNING id
                """, Long.class, code, code, owner.getId(), owner.getId());
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, 'OWNER', ?)
                """, id, owner.getId(), owner.getId());
        return id;
    }

    private long insertWorkItem(long projectId, AppUser creator) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO work_item (project_id, type, title, status, priority, created_by, updated_by)
                VALUES (?, 'TASK', '日报关联任务', 'IN_PROGRESS', 'NORMAL', ?, ?) RETURNING id
                """, Long.class, projectId, creator.getId(), creator.getId());
    }

    private long insertMeetingRecord(long projectId, AppUser creator) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO meeting_record (
                    project_id, type, title, occurred_at, attendees, status, created_by, updated_by
                ) VALUES (?, 'REGULAR_MEETING', '日报关联例会', CURRENT_TIMESTAMP, '[]', 'DRAFT', ?, ?)
                RETURNING id
                """, Long.class, projectId, creator.getId(), creator.getId());
    }

    private static UserRequestPostProcessor actor(String username, String... authorities) {
        return user(username).authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));
    }

    private static String request(
            String content, String hours, Long workItemId, Long meetingRecordId) {
        String workItemIds = workItemId == null ? "" : workItemId.toString();
        String meetingRecordIds = meetingRecordId == null ? "" : meetingRecordId.toString();
        return """
                {"reportDate":"%s","originalContent":"%s","workHours":%s,
                 "workItemIds":[%s],"meetingRecordIds":[%s],"deploymentRecordIds":[]}
                """.formatted(LocalDate.now(), content, hours, workItemIds, meetingRecordIds);
    }
}
