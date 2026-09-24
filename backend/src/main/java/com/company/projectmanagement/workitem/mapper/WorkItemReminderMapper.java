package com.company.projectmanagement.workitem.mapper;

import com.company.projectmanagement.workitem.domain.WorkItemReminder;
import com.company.projectmanagement.workitem.domain.WorkItemReminderStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 个人提醒查询始终带创建人条件，避免同项目成员互相看到提醒。 */
@Mapper
public interface WorkItemReminderMapper {

    String COLUMNS = """
            reminder.id, reminder.project_id, reminder.work_item_id,
            item.title AS work_item_title, item.type AS work_item_type,
            item.status AS work_item_status, reminder.remind_at, reminder.message,
            reminder.status, reminder.created_by, reminder.created_at,
            reminder.dismissed_at, reminder.deleted_at
            """;

    @Insert("""
            INSERT INTO work_item_reminder (
                project_id, work_item_id, remind_at, message, status, created_by
            ) VALUES (
                #{projectId}, #{workItemId}, #{remindAt}, #{message}, #{status}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkItemReminder reminder);

    @Select("""
            <script>
            SELECT count(*)
            FROM work_item_reminder reminder
            JOIN work_item item ON item.id = reminder.work_item_id
            WHERE reminder.project_id = #{projectId}
              AND reminder.created_by = #{actorUserId}
              AND reminder.deleted_at IS NULL AND item.deleted_at IS NULL
            <if test='workItemId != null'>AND reminder.work_item_id = #{workItemId}</if>
            <if test='status != null'>AND reminder.status = #{status}</if>
            <if test='from != null'>AND reminder.remind_at &gt;= #{from}</if>
            <if test='to != null'>AND reminder.remind_at &lt;= #{to}</if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("actorUserId") Long actorUserId,
            @Param("workItemId") Long workItemId,
            @Param("status") WorkItemReminderStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM work_item_reminder reminder
            JOIN work_item item ON item.id = reminder.work_item_id
            WHERE reminder.project_id = #{projectId}
              AND reminder.created_by = #{actorUserId}
              AND reminder.deleted_at IS NULL AND item.deleted_at IS NULL
            <if test='workItemId != null'>AND reminder.work_item_id = #{workItemId}</if>
            <if test='status != null'>AND reminder.status = #{status}</if>
            <if test='from != null'>AND reminder.remind_at &gt;= #{from}</if>
            <if test='to != null'>AND reminder.remind_at &lt;= #{to}</if>
            ORDER BY CASE WHEN reminder.status = 'PENDING' THEN 0 ELSE 1 END,
                     reminder.remind_at, reminder.id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WorkItemReminder> selectPage(
            @Param("projectId") Long projectId,
            @Param("actorUserId") Long actorUserId,
            @Param("workItemId") Long workItemId,
            @Param("status") WorkItemReminderStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM work_item_reminder reminder
            JOIN work_item item ON item.id = reminder.work_item_id
            WHERE reminder.project_id = #{projectId} AND reminder.id = #{reminderId}
              AND reminder.created_by = #{actorUserId}
            """)
    WorkItemReminder selectOwnedByIdIncludingDeleted(
            @Param("projectId") Long projectId,
            @Param("reminderId") Long reminderId,
            @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE work_item_reminder
            SET status = 'DISMISSED', dismissed_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{reminderId}
              AND created_by = #{actorUserId} AND status = 'PENDING'
              AND deleted_at IS NULL
            """)
    int dismiss(
            @Param("projectId") Long projectId,
            @Param("reminderId") Long reminderId,
            @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE work_item_reminder SET deleted_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{reminderId}
              AND created_by = #{actorUserId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("reminderId") Long reminderId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'WORK_ITEM_REMINDER', #{reminderId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId}, 'workItemId', #{workItemId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("reminderId") String reminderId,
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId);
}
