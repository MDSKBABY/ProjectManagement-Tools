package com.company.projectmanagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.projectmanagement.identity.domain.AppUser;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(properties={"app.bootstrap-admin.username=delivery-admin","app.bootstrap-admin.password=Test-only-delivery-password-123!"})
@AutoConfigureMockMvc @Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectDeliveryRecordsApiTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.11-alpine");
    @Autowired MockMvc mockMvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;

    @AfterEach void clean(){
        jdbc.update("DELETE FROM file_link WHERE business_type IN ('VENDOR_INTERFACE','MEETING_RECORD','DESIGN_ASSET')");
        jdbc.update("DELETE FROM integration_interface"); jdbc.update("DELETE FROM vendor_record");
        jdbc.update("DELETE FROM meeting_record"); jdbc.update("DELETE FROM design_asset");
        jdbc.update("DELETE FROM file_asset"); jdbc.update("DELETE FROM work_item_status_log"); jdbc.update("DELETE FROM work_item");
        jdbc.update("DELETE FROM project"); jdbc.update("DELETE FROM app_user WHERE username <> 'delivery-admin'");
    }

    @Test void vendorInterfaceRequiresAttachmentBeforeSubmission() throws Exception {
        Context c=context("DELIVERY-VENDOR");
        long vendorId=json.readTree(mockMvc.perform(post("/api/v1/projects/{id}/vendors",c.projectId)
                .with(user(c.user.getUsername()).authorities(new SimpleGrantedAuthority("vendor_record:write"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"设备厂商\",\"contactName\":\"张工\",\"contactPhone\":\"13800000000\",\"systemName\":\"告警平台\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String request="""
                {"vendorId":%d,"name":"告警推送","direction":"INBOUND","protocol":"HTTPS",
                 "endpoint":"https://vendor.example/api/alarm","authMethod":"TOKEN",
                 "fieldDescription":"alarmId、level","pushFrequency":"实时","attachmentIds":[]}
                """.formatted(vendorId);
        long interfaceId=json.readTree(mockMvc.perform(post("/api/v1/projects/{id}/interfaces",c.projectId)
                .with(user(c.user.getUsername()).authorities(new SimpleGrantedAuthority("vendor_record:write"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/projects/{id}/interfaces/{interfaceId}/submit",c.projectId,interfaceId)
                .with(user(c.user.getUsername()).authorities(new SimpleGrantedAuthority("vendor_record:write"))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INTERFACE_ATTACHMENT_REQUIRED"));
    }

    @Test void meetingSubmissionRequiresMinutesAndAttachment() throws Exception {
        Context c=context("DELIVERY-MEETING");
        String body=mockMvc.perform(post("/api/v1/projects/{id}/meeting-records",c.projectId)
                .with(user(c.user.getUsername()).authorities(new SimpleGrantedAuthority("meeting_record:write"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"TRAINING","title":"系统培训","occurredAt":"2026-09-24T10:00:00+08:00",
                 "attendees":["张工"],"minutes":"","actionItems":"补充手册","attachmentIds":[]}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(body).get("id").asLong();
        mockMvc.perform(post("/api/v1/projects/{projectId}/meeting-records/{id}/submit",c.projectId,id)
                .with(user(c.user.getUsername()).authorities(new SimpleGrantedAuthority("meeting_record:write"))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MEETING_MINUTES_REQUIRED"));
    }

    @Test void designAssetRejectsRequirementFromAnotherProject() throws Exception {
        Context home=context("DELIVERY-DESIGN-A"); Context other=context("DELIVERY-DESIGN-B");
        Long workId=jdbc.queryForObject("""
            INSERT INTO work_item(project_id,type,title,status,priority,created_by,updated_by)
            VALUES(?,'TASK','外部需求','TODO','NORMAL',?,?) RETURNING id
            """,Long.class,other.projectId,other.user.getId(),other.user.getId());
        mockMvc.perform(post("/api/v1/projects/{id}/design-assets",home.projectId)
                .with(user(home.user.getUsername()).authorities(new SimpleGrantedAuthority("design_asset:write"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"PROTOTYPE","title":"首页原型","stage":"需求阶段","version":"v1",
                 "externalUrl":"https://design.example/prototype","description":"首版", "requirementWorkItemId":%d,
                 "attachmentIds":[]}
                """.formatted(workId))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORK_ITEM_NOT_FOUND"));
    }

    private Context context(String code){
        Long uid=jdbc.queryForObject("INSERT INTO app_user(username,password_hash,display_name) VALUES(?, '{bcrypt}$2a$12$test', ?) RETURNING id",Long.class,code.toLowerCase(),code);
        AppUser u=new AppUser();u.setId(uid);u.setUsername(code.toLowerCase());
        Long pid=jdbc.queryForObject("INSERT INTO project(code,name,status,owner_id,created_by) VALUES(?,?,'PLANNING',?,?) RETURNING id",Long.class,code,code,uid,uid);
        jdbc.update("INSERT INTO project_member(project_id,user_id,project_role,created_by) VALUES(?,?,'OWNER',?)",pid,uid,uid);
        return new Context(pid,u);
    }
    private record Context(long projectId,AppUser user){}
}
