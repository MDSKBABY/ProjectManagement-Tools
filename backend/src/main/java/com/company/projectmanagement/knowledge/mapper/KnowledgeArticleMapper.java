package com.company.projectmanagement.knowledge.mapper;

import com.company.projectmanagement.file.domain.FileAsset;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticle;
import com.company.projectmanagement.knowledge.domain.KnowledgeArticleStatus;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 技术知识查询始终限定项目；附件通过统一文件关联表读取。 */
@Mapper
public interface KnowledgeArticleMapper {

    String COLUMNS = """
            article.id, article.project_id, article.title, article.scenario,
            article.symptom, article.cause, article.solution, article.applicable_conditions,
            article.tags::text AS tags_json, article.status, article.review_comment,
            article.reviewed_by, reviewer.display_name AS reviewed_by_display_name,
            article.reviewed_at, article.created_by,
            creator.display_name AS created_by_display_name, article.created_at,
            article.updated_by, article.updated_at, article.deleted_at,
            (SELECT count(*)::int FROM file_link link
             JOIN file_asset file ON file.id = link.file_asset_id
             WHERE link.business_type = 'KNOWLEDGE_ARTICLE'
               AND link.business_id = article.id
               AND file.deleted_at IS NULL) AS attachment_count
            """;

    @Insert("""
            INSERT INTO knowledge_article (
                project_id, title, scenario, symptom, cause, solution,
                applicable_conditions, tags, status, created_by, updated_by
            ) VALUES (
                #{projectId}, #{title}, #{scenario}, #{symptom}, #{cause}, #{solution},
                #{applicableConditions}, CAST(#{tagsJson} AS jsonb), #{status},
                #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeArticle article);

    @Select("""
            SELECT
            """ + COLUMNS + """
            FROM knowledge_article article
            JOIN app_user creator ON creator.id = article.created_by
            LEFT JOIN app_user reviewer ON reviewer.id = article.reviewed_by
            WHERE article.project_id = #{projectId} AND article.id = #{articleId}
              AND article.deleted_at IS NULL
            """)
    KnowledgeArticle selectById(
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId);

    @Select("""
            <script>
            SELECT count(*) FROM knowledge_article article
            WHERE article.project_id = #{projectId} AND article.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(article.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.scenario) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.symptom) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.cause) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.solution) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>AND article.status = #{status}</if>
            <if test='tag != null'>AND jsonb_exists(article.tags, #{tag})</if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") KnowledgeArticleStatus status,
            @Param("tag") String tag);

    @Select("""
            <script>
            SELECT
            """ + COLUMNS + """
            FROM knowledge_article article
            JOIN app_user creator ON creator.id = article.created_by
            LEFT JOIN app_user reviewer ON reviewer.id = article.reviewed_by
            WHERE article.project_id = #{projectId} AND article.deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(article.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.scenario) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.symptom) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.cause) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(article.solution) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>AND article.status = #{status}</if>
            <if test='tag != null'>AND jsonb_exists(article.tags, #{tag})</if>
            ORDER BY article.updated_at DESC, article.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<KnowledgeArticle> selectPage(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("status") KnowledgeArticleStatus status,
            @Param("tag") String tag,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Update("""
            UPDATE knowledge_article
            SET title = #{title}, scenario = #{scenario}, symptom = #{symptom},
                cause = #{cause}, solution = #{solution},
                applicable_conditions = #{applicableConditions},
                tags = CAST(#{tagsJson} AS jsonb), status = 'DRAFT',
                review_comment = NULL, reviewed_by = NULL, reviewed_at = NULL,
                updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{id} AND deleted_at IS NULL
              AND status IN ('DRAFT', 'REJECTED')
            """)
    int update(KnowledgeArticle article);

    @Update("""
            UPDATE knowledge_article
            SET status = 'PENDING_REVIEW', review_comment = NULL,
                reviewed_by = NULL, reviewed_at = NULL,
                updated_by = #{actorUserId}, updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{articleId}
              AND status IN ('DRAFT', 'REJECTED') AND deleted_at IS NULL
            """)
    int submit(
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId,
            @Param("actorUserId") Long actorUserId);

    @Update("""
            UPDATE knowledge_article
            SET status = #{status}, review_comment = #{comment}, reviewed_by = #{reviewerId},
                reviewed_at = CURRENT_TIMESTAMP, updated_by = #{reviewerId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{articleId}
              AND status = 'PENDING_REVIEW' AND deleted_at IS NULL
            """)
    int review(
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId,
            @Param("status") KnowledgeArticleStatus status,
            @Param("comment") String comment,
            @Param("reviewerId") Long reviewerId);

    @Update("""
            UPDATE knowledge_article
            SET deleted_at = CURRENT_TIMESTAMP, updated_by = #{actorUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE project_id = #{projectId} AND id = #{articleId}
              AND status IN ('DRAFT', 'REJECTED') AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId,
            @Param("actorUserId") Long actorUserId);

    @Insert("""
            INSERT INTO file_link (
                file_asset_id, project_id, business_type, business_id, created_by
            ) VALUES (
                #{fileAssetId}, #{projectId}, 'KNOWLEDGE_ARTICLE', #{articleId}, #{createdBy}
            )
            """)
    int linkAttachment(
            @Param("fileAssetId") Long fileAssetId,
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId,
            @Param("createdBy") Long createdBy);

    @Delete("""
            DELETE FROM file_link
            WHERE project_id = #{projectId} AND business_type = 'KNOWLEDGE_ARTICLE'
              AND business_id = #{articleId}
            """)
    int deleteAttachments(
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId);

    @Select("""
            SELECT file.id, file.file_group_id, file.version, file.original_name,
                   file.storage_key, file.media_type, file.size_bytes, file.sha256,
                   file.status, file.uploaded_by,
                   uploader.display_name AS uploaded_by_display_name,
                   file.created_at, file.updated_at, file.deleted_at
            FROM file_link link
            JOIN file_asset file ON file.id = link.file_asset_id
            JOIN app_user uploader ON uploader.id = file.uploaded_by
            WHERE link.project_id = #{projectId}
              AND link.business_type = 'KNOWLEDGE_ARTICLE'
              AND link.business_id = #{articleId}
              AND file.deleted_at IS NULL
            ORDER BY link.created_at, link.id
            """)
    List<FileAsset> selectAttachments(
            @Param("projectId") Long projectId,
            @Param("articleId") Long articleId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'KNOWLEDGE_ARTICLE', #{articleId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("articleId") String articleId,
            @Param("projectId") Long projectId);
}
