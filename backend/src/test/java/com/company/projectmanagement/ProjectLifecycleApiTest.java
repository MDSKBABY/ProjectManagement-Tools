package com.company.projectmanagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties={"app.bootstrap-admin.username=lifecycle-admin","app.bootstrap-admin.password=Test-only-lifecycle-password-123!"})
@AutoConfigureMockMvc @Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectLifecycleApiTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11-alpine");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    @Test void aggregatesMilestoneAndComputesOverdueOnServer() throws Exception {
        Long userId=jdbc.queryForObject("INSERT INTO app_user(username,password_hash,display_name) VALUES('lifecycle-owner','{bcrypt}$2a$12$test','节点负责人') RETURNING id",Long.class);
        Long projectId=jdbc.queryForObject("INSERT INTO project(code,name,status,owner_id,created_by) VALUES('LIFE','周期项目','ACTIVE',?,?) RETURNING id",Long.class,userId,userId);
        jdbc.update("INSERT INTO project_member(project_id,user_id,project_role,created_by) VALUES(?,?,'OWNER',?)",projectId,userId,userId);
        jdbc.update("INSERT INTO work_item(project_id,type,title,status,priority,planned_end_date,created_by,updated_by) VALUES(?,'MILESTONE','过期里程碑','TODO','HIGH','2020-01-02',?,?)",projectId,userId,userId);
        mvc.perform(get("/api/v1/projects/{id}/lifecycle",projectId).param("from","2020-01-01").param("to","2030-01-01")
                .with(user("lifecycle-owner").authorities(new SimpleGrantedAuthority("project_lifecycle:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("MILESTONE"))
                .andExpect(jsonPath("$[0].scheduleStatus").value("OVERDUE"));
    }
}
