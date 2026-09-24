package com.company.projectmanagement.workitem.mapper;

import com.company.projectmanagement.workitem.domain.WorkItem;
import com.company.projectmanagement.workitem.domain.WorkItemPriority;
import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemStatusLog;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 工作项查询均限定项目且默认排除软删除记录。 */
@Mapper
public interface WorkItemMapper {

    String COLUMNS = """
            item.id, item.project_id, item.type, item.title, item.description,
            item.status, item.priority, item.assignee_id,
            assignee.display_name AS assignee_display_name,
            item.planned_start_date, item.planned_end_date,
            item.actual_start_date, item.actual_end_date,
            item.created_by, item.created_at, item.updated_by, item.updated_at, item.deleted_at
            """;

    @Insert("""
            INSERT INTO work_item (
                project_id, type, title, description, status, priority, assignee_id,
                planned_start_date, planned_end_date, created_by
            ) VALUES (
                #{projectId}, #{type}, #{title}, #{description}, #{status}, #{priority},
                #{assigneeId}, #{plannedStartDate}, #{plannedEndDate}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkItem item);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM work_item item
            LEFT JOIN app_user assignee ON assignee.id = item.assignee_id
            WHERE item.project_id = #{projectId} AND item.id = #{workItemId}
              AND item.deleted_at IS NULL
            """)
    WorkItem selectById(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM work_item item
            LEFT JOIN app_user assignee ON assignee.id = item.assignee_id
            WHERE item.project_id = #{projectId} AND item.id = #{workItemId}
            """)
    WorkItem selectByIdIncludingDeleted(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId);

    @Select("""
            <script>
            SELECT count(*) FROM work_item item
            WHERE item.project_id = #{projectId} AND item.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(item.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(item.description, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='type != null'>AND item.type = #{type}</if>
            <if test='status != null'>AND item.status = #{status}</if>
            <if test='priority != null'>AND item.priority = #{priority}</if>
            <if test='assigneeId != null'>AND item.assignee_id = #{assigneeId}</if>
            <if test='plannedFrom != null'>
              AND COALESCE(item.planned_end_date, item.planned_start_date) &gt;= #{plannedFrom}
            </if>
            <if test='plannedTo != null'>
              AND COALESCE(item.planned_start_date, item.planned_end_date) &lt;= #{plannedTo}
            </if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("type") WorkItemType type,
            @Param("status") WorkItemStatus status,
            @Param("priority") WorkItemPriority priority,
            @Param("assigneeId") Long assigneeId,
            @Param("plannedFrom") LocalDate plannedFrom,
            @Param("plannedTo") LocalDate plannedTo);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM work_item item
            LEFT JOIN app_user assignee ON assignee.id = item.assignee_id
            WHERE item.project_id = #{projectId} AND item.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(item.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(COALESCE(item.description, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='type != null'>AND item.type = #{type}</if>
            <if test='status != null'>AND item.status = #{status}</if>
            <if test='priority != null'>AND item.priority = #{priority}</if>
            <if test='assigneeId != null'>AND item.assignee_id = #{assigneeId}</if>
            <if test='plannedFrom != null'>
              AND COALESCE(item.planned_end_date, item.planned_start_date) &gt;= #{plannedFrom}
            </if>
            <if test='plannedTo != null'>
              AND COALESCE(item.planned_start_date, item.planned_end_date) &lt;= #{plannedTo}
            </if>
            ORDER BY item.updated_at DESC, item.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WorkItem> selectPage(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("type") WorkItemType type,
            @Param("status") WorkItemStatus status,
            @Param("priority") WorkItemPriority priority,
            @Param("assigneeId") Long assigneeId,
            @Param("plannedFrom") LocalDate plannedFrom,
            @Param("plannedTo") LocalDate plannedTo,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Update("""
            UPDATE work_item
            SET type = #{type}, title = #{title}, description = #{description},
                priority = #{priority}, assignee_id = #{assigneeId},
                planned_start_date = #{plannedStartDate}, planned_end_date = #{plannedEndDate},
                updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{id} AND deleted_at IS NULL
            """)
    int updateMetadata(WorkItem item);

    @Update("""
            UPDATE work_item
            SET status = #{toStatus}, actual_start_date = #{actualStartDate},
                actual_end_date = #{actualEndDate}, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{workItemId}
              AND status = #{fromStatus} AND deleted_at IS NULL
            """)
    int updateStatus(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId,
            @Param("fromStatus") WorkItemStatus fromStatus,
            @Param("toStatus") WorkItemStatus toStatus,
            @Param("actualStartDate") LocalDate actualStartDate,
            @Param("actualEndDate") LocalDate actualEndDate,
            @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE work_item
            SET deleted_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{workItemId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO work_item_status_log (
                work_item_id, from_status, to_status, comment, changed_by
            ) VALUES (
                #{workItemId}, #{fromStatus}, #{toStatus}, #{comment}, #{changedBy}
            )
            """)
    int insertStatusLog(
            @Param("workItemId") Long workItemId,
            @Param("fromStatus") WorkItemStatus fromStatus,
            @Param("toStatus") WorkItemStatus toStatus,
            @Param("comment") String comment,
            @Param("changedBy") Long changedBy);

    @Select("""
            SELECT log.id, log.work_item_id, log.from_status, log.to_status, log.comment,
                   log.changed_by, actor.display_name AS changed_by_display_name, log.changed_at
            FROM work_item_status_log log
            JOIN app_user actor ON actor.id = log.changed_by
            WHERE log.work_item_id = #{workItemId}
            ORDER BY log.changed_at, log.id
            """)
    List<WorkItemStatusLog> selectStatusHistory(@Param("workItemId") Long workItemId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'WORK_ITEM', #{workItemId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("workItemId") String workItemId,
            @Param("projectId") Long projectId);
}
