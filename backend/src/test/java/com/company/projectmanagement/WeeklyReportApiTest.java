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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.bootstrap-admin.username=weekly-admin",
        "app.bootstrap-admin.password=Test-only-weekly-password-123!"
})
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class WeeklyReportApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.11-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM weekly_report");
        jdbcTemplate.update("DELETE FROM daily_report_deployment_record");
        jdbcTemplate.update("DELETE FROM daily_report_work_item");
        jdbcTemplate.update("DELETE FROM daily_report");
        jdbcTemplate.update("DELETE FROM project");
        jdbcTemplate.update("DELETE FROM app_user WHERE username <> 'weekly-admin'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE resource_type = 'WEEKLY_REPORT'");
    }

    @Test
    void generatesFromConfirmedDailyReportsAndKeepsSnapshotImmutable() throws Exception {
        AppUser owner = insertUser();
        long projectId = insertProject(owner);
        LocalDate monday = LocalDate.of(2026, 9, 21);
        insertConfirmedDaily(projectId, owner, monday, "完成环境部署", "完成项目环境部署。", "8.0");

        String request = """
                {"periodStart":"2026-09-21","periodEnd":"2026-09-27","nextWeekPlan":"推进联调"}
                """;
        String body = mockMvc.perform(post("/api/v1/projects/{projectId}/weekly-reports/generate", projectId)
                        .with(user(owner.getUsername()).authorities(
                                new SimpleGrantedAuthority("weekly_report:write")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generationMethod").value("AUTO_SUMMARY"))
                .andExpect(jsonPath("$.dailySnapshots.length()").value(1))
                .andExpect(jsonPath("$.dailySnapshots[0].content").value("完成项目环境部署。"))
                .andReturn().getResponse().getContentAsString();
        long reportId = objectMapper.readTree(body).get("id").asLong();

        insertConfirmedDaily(projectId, owner, monday.plusDays(1), "后来新增", null, "2.0");
        mockMvc.perform(get("/api/v1/projects/{projectId}/weekly-reports/{reportId}", projectId, reportId)
                        .with(user(owner.getUsername()).authorities(
                                new SimpleGrantedAuthority("weekly_report:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailySnapshots.length()").value(1));

        mockMvc.perform(post("/api/v1/projects/{projectId}/weekly-reports/{reportId}/confirm", projectId, reportId)
                        .with(user(owner.getUsername()).authorities(
                                new SimpleGrantedAuthority("weekly_report:confirm"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(put("/api/v1/projects/{projectId}/weekly-reports/{reportId}", projectId, reportId)
                        .with(user(owner.getUsername()).authorities(
                                new SimpleGrantedAuthority("weekly_report:write"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"覆盖\",\"nextWeekPlan\":\"覆盖\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WEEKLY_REPORT_LOCKED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT jsonb_array_length(daily_snapshot) FROM weekly_report WHERE id = ?",
                Integer.class, reportId)).isEqualTo(1);
    }

    private AppUser insertUser() {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO app_user (username, password_hash, display_name)
                VALUES ('weekly-owner', '{bcrypt}$2a$12$test', '周报负责人') RETURNING id
                """, Long.class);
        AppUser user = new AppUser(); user.setId(id); user.setUsername("weekly-owner"); return user;
    }

    private long insertProject(AppUser owner) {
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO project (code, name, status, owner_id, created_by)
                VALUES ('WEEKLY', '周报项目', 'PLANNING', ?, ?) RETURNING id
                """, Long.class, owner.getId(), owner.getId());
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, project_role, created_by)
                VALUES (?, ?, 'OWNER', ?)
                """, id, owner.getId(), owner.getId());
        return id;
    }

    private void insertConfirmedDaily(long projectId, AppUser user, LocalDate date,
                                      String original, String polished, String hours) {
        jdbcTemplate.update("""
                INSERT INTO daily_report (
                    project_id, report_date, reporter_id, original_content, polished_content,
                    work_hours, status, confirmed_by, confirmed_at, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?::numeric, 'CONFIRMED', ?, CURRENT_TIMESTAMP, ?, ?)
                """, projectId, date, user.getId(), original, polished, hours,
                user.getId(), user.getId(), user.getId());
    }
}
