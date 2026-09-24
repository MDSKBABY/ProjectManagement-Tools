package com.company.projectmanagement.workitem.mapper;

import com.company.projectmanagement.workitem.domain.WorkItemRelation;
import com.company.projectmanagement.workitem.domain.WorkItemRelationType;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 工作项关系持久层，负责有界分页、图判环和软删除。 */
@Mapper
public interface WorkItemRelationMapper {

    String COLUMNS = """
            relation.id, relation.project_id, relation.source_work_item_id,
            source_item.title AS source_title, source_item.type AS source_type,
            source_item.status AS source_status, relation.target_work_item_id,
            target_item.title AS target_title, target_item.type AS target_type,
            target_item.status AS target_status, relation.type, relation.created_by,
            creator.display_name AS created_by_display_name, relation.created_at,
            relation.deleted_by, relation.deleted_at
            """;

    /** 同一项目的关系写入串行化，防止并发请求同时绕过判环。 */
    @Select("SELECT id FROM project WHERE id = #{projectId} FOR UPDATE")
    Long lockProject(@Param("projectId") Long projectId);

    /** 关系创建期间锁定端点，避免与工作项软删除产生竞态。 */
    @Select("""
            SELECT id FROM work_item
            WHERE project_id = #{projectId} AND id = #{workItemId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockActiveWorkItem(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId);

    @Insert("""
            INSERT INTO work_item_relation (
                project_id, source_work_item_id, target_work_item_id, type, created_by
            ) VALUES (
                #{projectId}, #{sourceWorkItemId}, #{targetWorkItemId}, #{type}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WorkItemRelation relation);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM work_item_relation relation
            JOIN work_item source_item ON source_item.id = relation.source_work_item_id
            JOIN work_item target_item ON target_item.id = relation.target_work_item_id
            JOIN app_user creator ON creator.id = relation.created_by
            WHERE relation.project_id = #{projectId} AND relation.id = #{relationId}
              AND relation.deleted_at IS NULL
              AND source_item.deleted_at IS NULL AND target_item.deleted_at IS NULL
            """)
    WorkItemRelation selectById(
            @Param("projectId") Long projectId,
            @Param("relationId") Long relationId);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM work_item_relation relation
            JOIN work_item source_item ON source_item.id = relation.source_work_item_id
            JOIN work_item target_item ON target_item.id = relation.target_work_item_id
            JOIN app_user creator ON creator.id = relation.created_by
            WHERE relation.project_id = #{projectId} AND relation.id = #{relationId}
            """)
    WorkItemRelation selectByIdIncludingDeleted(
            @Param("projectId") Long projectId,
            @Param("relationId") Long relationId);

    @Select("""
            <script>
            SELECT count(*)
            FROM work_item_relation relation
            JOIN work_item source_item ON source_item.id = relation.source_work_item_id
            JOIN work_item target_item ON target_item.id = relation.target_work_item_id
            WHERE relation.project_id = #{projectId} AND relation.deleted_at IS NULL
              AND source_item.deleted_at IS NULL AND target_item.deleted_at IS NULL
            <if test='workItemId != null'>
              AND (relation.source_work_item_id = #{workItemId}
                   OR relation.target_work_item_id = #{workItemId})
            </if>
            <if test='type != null'>AND relation.type = #{type}</if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId,
            @Param("type") WorkItemRelationType type);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM work_item_relation relation
            JOIN work_item source_item ON source_item.id = relation.source_work_item_id
            JOIN work_item target_item ON target_item.id = relation.target_work_item_id
            JOIN app_user creator ON creator.id = relation.created_by
            WHERE relation.project_id = #{projectId} AND relation.deleted_at IS NULL
              AND source_item.deleted_at IS NULL AND target_item.deleted_at IS NULL
            <if test='workItemId != null'>
              AND (relation.source_work_item_id = #{workItemId}
                   OR relation.target_work_item_id = #{workItemId})
            </if>
            <if test='type != null'>AND relation.type = #{type}</if>
            ORDER BY relation.created_at DESC, relation.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WorkItemRelation> selectPage(
            @Param("projectId") Long projectId,
            @Param("workItemId") Long workItemId,
            @Param("type") WorkItemRelationType type,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM work_item_relation relation
                WHERE relation.project_id = #{projectId}
                  AND relation.source_work_item_id = #{sourceWorkItemId}
                  AND relation.target_work_item_id = #{targetWorkItemId}
                  AND relation.deleted_at IS NULL
                  AND ((#{hierarchy} = TRUE AND relation.type = 'PARENT_CHILD')
                       OR (#{hierarchy} = FALSE
                           AND relation.type IN ('PRECEDES', 'BLOCKS')))
            )
            """)
    boolean existsGraphEdge(
            @Param("projectId") Long projectId,
            @Param("sourceWorkItemId") Long sourceWorkItemId,
            @Param("targetWorkItemId") Long targetWorkItemId,
            @Param("hierarchy") boolean hierarchy);

    @Select("""
            WITH RECURSIVE reachable(work_item_id) AS (
                SELECT relation.target_work_item_id
                FROM work_item_relation relation
                JOIN work_item source_item ON source_item.id = relation.source_work_item_id
                JOIN work_item target_item ON target_item.id = relation.target_work_item_id
                WHERE relation.project_id = #{projectId}
                  AND relation.source_work_item_id = #{targetWorkItemId}
                  AND relation.deleted_at IS NULL
                  AND source_item.deleted_at IS NULL AND target_item.deleted_at IS NULL
                  AND ((#{hierarchy} = TRUE AND relation.type = 'PARENT_CHILD')
                       OR (#{hierarchy} = FALSE
                           AND relation.type IN ('PRECEDES', 'BLOCKS')))
                UNION
                SELECT relation.target_work_item_id
                FROM work_item_relation relation
                JOIN reachable path ON path.work_item_id = relation.source_work_item_id
                JOIN work_item source_item ON source_item.id = relation.source_work_item_id
                JOIN work_item target_item ON target_item.id = relation.target_work_item_id
                WHERE relation.project_id = #{projectId}
                  AND relation.deleted_at IS NULL
                  AND source_item.deleted_at IS NULL AND target_item.deleted_at IS NULL
                  AND ((#{hierarchy} = TRUE AND relation.type = 'PARENT_CHILD')
                       OR (#{hierarchy} = FALSE
                           AND relation.type IN ('PRECEDES', 'BLOCKS')))
            )
            SELECT EXISTS (
                SELECT 1 FROM reachable WHERE work_item_id = #{sourceWorkItemId}
            )
            """)
    boolean wouldCreateCycle(
            @Param("projectId") Long projectId,
            @Param("sourceWorkItemId") Long sourceWorkItemId,
            @Param("targetWorkItemId") Long targetWorkItemId,
            @Param("hierarchy") boolean hierarchy);

    @Update("""
            UPDATE work_item_relation
            SET deleted_by = #{actorUserId}, deleted_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{relationId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("relationId") Long relationId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'WORK_ITEM_RELATION', #{relationId}, 'SUCCESS',
                jsonb_build_object(
                    'projectId', #{projectId},
                    'type', CAST(#{type} AS text),
                    'sourceWorkItemId', #{sourceWorkItemId},
                    'targetWorkItemId', #{targetWorkItemId}
                )
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("relationId") String relationId,
            @Param("projectId") Long projectId,
            @Param("type") WorkItemRelationType type,
            @Param("sourceWorkItemId") Long sourceWorkItemId,
            @Param("targetWorkItemId") Long targetWorkItemId);
}
