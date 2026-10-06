package com.company.projectmanagement.delivery.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.file.domain.FileStatus;
import com.company.projectmanagement.file.mapper.FileAssetMapper;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import com.company.projectmanagement.workitem.mapper.WorkItemMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 厂商接口、会议培训和设计资料共享的项目边界与附件校验。 */
@Service
public class ProjectDeliveryRecordService {
    private final JdbcTemplate jdbc;
    private final ProjectMapper projects;
    private final AppUserMapper users;
    private final IdentityAccessMapper access;
    private final FileAssetMapper files;
    private final WorkItemMapper workItems;
    private final ObjectMapper json;

    public ProjectDeliveryRecordService(JdbcTemplate jdbc, ProjectMapper projects, AppUserMapper users,
            IdentityAccessMapper access, FileAssetMapper files, WorkItemMapper workItems, ObjectMapper json) {
        this.jdbc = jdbc;
        this.projects = projects;
        this.users = users;
        this.access = access;
        this.files = files;
        this.workItems = workItems;
        this.json = json;
    }

    public List<Map<String, Object>> listVendors(long projectId, String username) {
        visible(projectId, username);
        return rows("""
                SELECT id, name, contact_name, contact_phone, contact_email, system_name, notes, updated_at
                FROM vendor_record
                WHERE project_id = ? AND deleted_at IS NULL
                ORDER BY updated_at DESC
                """, projectId);
    }

    public List<Map<String, Object>> listInterfaces(long projectId, String username) {
        visible(projectId, username);
        return rows("""
                SELECT i.id, i.vendor_id, v.name vendor_name, i.name, i.direction, i.protocol,
                       i.endpoint, i.auth_method, i.field_description, i.push_frequency,
                       i.integration_status, i.status, i.updated_at
                FROM integration_interface i
                JOIN vendor_record v ON v.id = i.vendor_id
                WHERE i.project_id = ? AND i.deleted_at IS NULL
                ORDER BY i.updated_at DESC
                """, projectId);
    }

    public List<Map<String, Object>> listMeetings(long projectId, String username) {
        visible(projectId, username);
        return rows("""
                SELECT id, type, title, occurred_at, attendees, minutes, action_items, status, updated_at
                FROM meeting_record
                WHERE project_id = ? AND deleted_at IS NULL
                ORDER BY occurred_at DESC
                """, projectId);
    }

    public List<Map<String, Object>> listDesigns(long projectId, String username) {
        visible(projectId, username);
        return rows("""
                SELECT d.id, d.type, d.title, d.stage, d.version, d.external_url, d.description,
                       d.requirement_work_item_id, w.title requirement_title, d.updated_at
                FROM design_asset d
                LEFT JOIN work_item w ON w.id = d.requirement_work_item_id
                WHERE d.project_id = ? AND d.deleted_at IS NULL
                ORDER BY d.updated_at DESC
                """, projectId);
    }

    @Transactional
    public Map<String, Object> createVendor(long projectId, VendorInput input, String username) {
        Actor actor = writable(projectId, username);
        Long id = jdbc.queryForObject("""
                INSERT INTO vendor_record(
                    project_id, name, contact_name, contact_phone, contact_email,
                    system_name, notes, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, projectId, input.name().trim(), trim(input.contactName()),
                trim(input.contactPhone()), trim(input.contactEmail()), trim(input.systemName()),
                trim(input.notes()), actor.id(), actor.id());
        audit(actor.id(), "VENDOR_CREATED", "VENDOR_RECORD", id, projectId);
        return vendor(projectId, id);
    }

    @Transactional
    public Map<String, Object> createInterface(long projectId, InterfaceInput input, String username) {
        Actor actor = writable(projectId, username);
        requireVendor(projectId, input.vendorId());
        List<Long> attachments = validateFiles(projectId, input.attachmentIds());
        Long id = jdbc.queryForObject("""
                INSERT INTO integration_interface(
                    project_id, vendor_id, name, direction, protocol, endpoint, auth_method,
                    field_description, push_frequency, integration_status, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'NOT_STARTED'), ?, ?) RETURNING id
                """, Long.class, projectId, input.vendorId(), input.name().trim(), input.direction(),
                input.protocol().trim(), trim(input.endpoint()), trim(input.authMethod()),
                trim(input.fieldDescription()), trim(input.pushFrequency()), input.integrationStatus(),
                actor.id(), actor.id());
        link(projectId, "VENDOR_INTERFACE", id, attachments, actor.id());
        audit(actor.id(), "INTERFACE_CREATED", "VENDOR_INTERFACE", id, projectId);
        return integration(projectId, id);
    }

    @Transactional
    public Map<String, Object> submitInterface(long projectId, long id, String username) {
        Actor actor = writable(projectId, username);
        requireRow("integration_interface", projectId, id, "INTERFACE_NOT_FOUND", "接口记录不存在");
        if (attachmentCount("VENDOR_INTERFACE", id) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INTERFACE_ATTACHMENT_REQUIRED",
                    "正式提交接口记录前至少需要一个可用附件");
        }
        jdbc.update("""
                UPDATE integration_interface
                SET status = 'SUBMITTED', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE project_id = ? AND id = ? AND status = 'DRAFT'
                """, actor.id(), projectId, id);
        audit(actor.id(), "INTERFACE_SUBMITTED", "VENDOR_INTERFACE", id, projectId);
        return integration(projectId, id);
    }

    @Transactional
    public Map<String, Object> createMeeting(long projectId, MeetingInput input, String username) {
        Actor actor = writable(projectId, username);
        List<Long> attachments = validateFiles(projectId, input.attachmentIds());
        List<String> attendees = input.attendees() == null ? List.of() : input.attendees();
        Long id = jdbc.queryForObject("""
                INSERT INTO meeting_record(
                    project_id, type, title, occurred_at, attendees, minutes,
                    action_items, created_by, updated_by
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?) RETURNING id
                """, Long.class, projectId, input.type(), input.title().trim(), input.occurredAt(),
                write(attendees), trim(input.minutes()), trim(input.actionItems()), actor.id(), actor.id());
        link(projectId, "MEETING_RECORD", id, attachments, actor.id());
        audit(actor.id(), "MEETING_RECORD_CREATED", "MEETING_RECORD", id, projectId);
        return meeting(projectId, id);
    }

    @Transactional
    public Map<String, Object> submitMeeting(long projectId, long id, String username) {
        Actor actor = writable(projectId, username);
        Map<String, Object> row = meeting(projectId, id);
        if (!StringUtils.hasText((String) row.get("minutes"))) {
            throw new ApiException(HttpStatus.CONFLICT, "MEETING_MINUTES_REQUIRED",
                    "正式提交会议或培训记录前必须填写纪要");
        }
        if (attachmentCount("MEETING_RECORD", id) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "MEETING_ATTACHMENT_REQUIRED",
                    "正式提交会议或培训记录前至少需要一个可用附件");
        }
        jdbc.update("""
                UPDATE meeting_record
                SET status = 'SUBMITTED', updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE project_id = ? AND id = ? AND status = 'DRAFT'
                """, actor.id(), projectId, id);
        audit(actor.id(), "MEETING_RECORD_SUBMITTED", "MEETING_RECORD", id, projectId);
        return meeting(projectId, id);
    }

    @Transactional
    public Map<String, Object> createDesign(long projectId, DesignInput input, String username) {
        Actor actor = writable(projectId, username);
        if (input.requirementWorkItemId() != null
                && workItems.selectById(projectId, input.requirementWorkItemId()) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WORK_ITEM_NOT_FOUND", "关联需求不存在");
        }
        List<Long> attachments = validateFiles(projectId, input.attachmentIds());
        Long id = jdbc.queryForObject("""
                INSERT INTO design_asset(
                    project_id, type, title, stage, version, external_url, description,
                    requirement_work_item_id, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, projectId, input.type(), input.title().trim(), trim(input.stage()),
                trim(input.version()), trim(input.externalUrl()), trim(input.description()),
                input.requirementWorkItemId(), actor.id(), actor.id());
        link(projectId, "DESIGN_ASSET", id, attachments, actor.id());
        audit(actor.id(), "DESIGN_ASSET_CREATED", "DESIGN_ASSET", id, projectId);
        return design(projectId, id);
    }

    private Map<String, Object> vendor(long projectId, long id) {
        return one("""
                SELECT id, name, contact_name, contact_phone, contact_email, system_name, notes, updated_at
                FROM vendor_record
                WHERE project_id = ? AND id = ? AND deleted_at IS NULL
                """, projectId, id, "VENDOR_NOT_FOUND", "厂商不存在");
    }

    private Map<String, Object> integration(long projectId, long id) {
        return one("""
                SELECT i.id, i.vendor_id, v.name vendor_name, i.name, i.direction, i.protocol,
                       i.endpoint, i.auth_method, i.field_description, i.push_frequency,
                       i.integration_status, i.status, i.updated_at
                FROM integration_interface i
                JOIN vendor_record v ON v.id = i.vendor_id
                WHERE i.project_id = ? AND i.id = ? AND i.deleted_at IS NULL
                """, projectId, id, "INTERFACE_NOT_FOUND", "接口记录不存在");
    }

    private Map<String, Object> meeting(long projectId, long id) {
        return one("""
                SELECT id, type, title, occurred_at, attendees, minutes, action_items, status, updated_at
                FROM meeting_record
                WHERE project_id = ? AND id = ? AND deleted_at IS NULL
                """, projectId, id, "MEETING_RECORD_NOT_FOUND", "会议或培训记录不存在");
    }

    private Map<String, Object> design(long projectId, long id) {
        return one("""
                SELECT d.id, d.type, d.title, d.stage, d.version, d.external_url, d.description,
                       d.requirement_work_item_id, w.title requirement_title, d.updated_at
                FROM design_asset d
                LEFT JOIN work_item w ON w.id = d.requirement_work_item_id
                WHERE d.project_id = ? AND d.id = ? AND d.deleted_at IS NULL
                """, projectId, id, "DESIGN_ASSET_NOT_FOUND", "设计资料不存在");
    }

    private void requireVendor(long projectId, long id) {
        requireRow("vendor_record", projectId, id, "VENDOR_NOT_FOUND", "厂商不存在");
    }

    private void requireRow(String table, long projectId, long id, String code, String message) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE project_id = ? AND id = ? AND deleted_at IS NULL",
                Integer.class, projectId, id);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, code, message);
        }
    }

    private Map<String, Object> one(
            String sql, long projectId, long id, String code, String message) {
        List<Map<String, Object>> result = rows(sql, projectId, id);
        if (result.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, code, message);
        }
        return result.getFirst();
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (resultSet, rowNumber) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= resultSet.getMetaData().getColumnCount(); index++) {
                row.put(camel(resultSet.getMetaData().getColumnLabel(index)), resultSet.getObject(index));
            }
            return row;
        }, args);
    }

    private List<Long> validateFiles(long projectId, List<Long> requested) {
        List<Long> ids = requested == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(requested));
        for (Long id : ids) {
            var file = files.selectProjectFile(projectId, id);
            if (file == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在");
            }
            if (file.getStatus() != FileStatus.AVAILABLE) {
                throw new ApiException(HttpStatus.CONFLICT, "FILE_NOT_AVAILABLE", "文件尚未上传完成");
            }
        }
        return ids;
    }

    private void link(long projectId, String type, long businessId, List<Long> ids, long actorId) {
        for (Long id : ids) {
            jdbc.update("""
                    INSERT INTO file_link(file_asset_id, project_id, business_type, business_id, created_by)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, projectId, type, businessId, actorId);
        }
    }

    private int attachmentCount(String type, long id) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM file_link link
                JOIN file_asset file ON file.id = link.file_asset_id
                WHERE link.business_type = ? AND link.business_id = ?
                  AND file.status = 'AVAILABLE' AND file.deleted_at IS NULL
                """, Integer.class, type, id);
    }

    private Actor visible(long projectId, String username) {
        AppUser user = users.selectActiveByUsername(username);
        boolean administrator = user != null
                && access.selectRoleCodesByUserId(user.getId()).contains("ADMIN");
        if (user == null || projects.selectVisibleById(projectId, user.getId(), administrator) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
        }
        return new Actor(user.getId(), administrator);
    }

    private Actor writable(long projectId, String username) {
        Actor actor = visible(projectId, username);
        if (!actor.administrator() && !projects.canWriteFiles(projectId, actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_RECORD_ACCESS_DENIED", "无权维护该项目记录");
        }
        return actor;
    }

    private void audit(long actorId, String action, String type, long id, long projectId) {
        jdbc.update("""
                INSERT INTO audit_log(
                    actor_user_id, action, resource_type, resource_id, outcome, details
                ) VALUES (?, ?, ?, ?, 'SUCCESS', jsonb_build_object('projectId', ?))
                """, actorId, action, type, Long.toString(id), projectId);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化会议参会人", exception);
        }
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String camel(String value) {
        StringBuilder result = new StringBuilder();
        boolean uppercaseNext = false;
        for (char character : value.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;
            } else {
                result.append(uppercaseNext ? Character.toUpperCase(character) : character);
                uppercaseNext = false;
            }
        }
        return result.toString();
    }

    private record Actor(long id, boolean administrator) { }

    public record VendorInput(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 100) String contactName,
            @Size(max = 100) String contactPhone,
            @Size(max = 200) String contactEmail,
            @Size(max = 200) String systemName,
            @Size(max = 10000) String notes) { }

    public record InterfaceInput(
            @NotNull Long vendorId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "INBOUND|OUTBOUND|BIDIRECTIONAL") String direction,
            @NotBlank @Size(max = 50) String protocol,
            @Size(max = 1000) String endpoint,
            @Size(max = 200) String authMethod,
            @Size(max = 20000) String fieldDescription,
            @Size(max = 200) String pushFrequency,
            @Pattern(regexp = "NOT_STARTED|IN_PROGRESS|PASSED|BLOCKED") String integrationStatus,
            List<Long> attachmentIds) { }

    public record MeetingInput(
            @NotBlank @Pattern(regexp = "REGULAR_MEETING|PRESENTATION|TRAINING") String type,
            @NotBlank @Size(max = 200) String title,
            @NotNull OffsetDateTime occurredAt,
            List<String> attendees,
            @Size(max = 30000) String minutes,
            @Size(max = 20000) String actionItems,
            List<Long> attachmentIds) { }

    public record DesignInput(
            @NotBlank @Pattern(regexp = "PROTOTYPE|TRACKING_MAP|DESIGN_SPEC|OTHER") String type,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100) String stage,
            @Size(max = 50) String version,
            @Size(max = 1000) String externalUrl,
            @Size(max = 20000) String description,
            Long requirementWorkItemId,
            List<Long> attachmentIds) { }
}
