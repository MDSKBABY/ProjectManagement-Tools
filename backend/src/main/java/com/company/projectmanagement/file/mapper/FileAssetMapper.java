package com.company.projectmanagement.file.mapper;

import com.company.projectmanagement.file.domain.FileAsset;
import com.company.projectmanagement.file.domain.FileStatus;
import com.company.projectmanagement.file.domain.FileUploadChunk;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 文件元数据查询始终通过 file_link 收敛到具体项目。 */
@Mapper
public interface FileAssetMapper {

    @Insert("""
            INSERT INTO file_asset (
                file_group_id, version, original_name, storage_key, media_type,
                size_bytes, sha256, status, uploaded_by
            ) VALUES (
                CAST(#{fileGroupId} AS uuid), #{version}, #{originalName}, #{storageKey}, #{mediaType},
                #{sizeBytes}, #{sha256}, #{status}, #{uploadedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileAsset asset);

    @Insert("""
            INSERT INTO file_link (
                file_asset_id, project_id, business_type, business_id, created_by
            ) VALUES (
                #{fileAssetId}, #{projectId}, 'PROJECT_DOCUMENT', #{projectId}, #{createdBy}
            )
            """)
    int linkProjectDocument(
            @Param("fileAssetId") Long fileAssetId,
            @Param("projectId") Long projectId,
            @Param("createdBy") Long createdBy);

    @Select("""
            <script>
            SELECT count(*)
            FROM file_asset asset
            JOIN file_link link ON link.file_asset_id = asset.id
            WHERE link.project_id = #{projectId}
              AND link.business_type = 'PROJECT_DOCUMENT'
              AND asset.deleted_at IS NULL
            <if test='keyword != null'>
              AND LOWER(asset.original_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countProjectFiles(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT asset.id, asset.file_group_id, asset.version, asset.original_name,
                   asset.storage_key, asset.media_type, asset.size_bytes, asset.sha256,
                   asset.status, asset.uploaded_by,
                   uploader.display_name AS uploaded_by_display_name,
                   asset.created_at, asset.updated_at, asset.deleted_at
            FROM file_asset asset
            JOIN file_link link ON link.file_asset_id = asset.id
            JOIN app_user uploader ON uploader.id = asset.uploaded_by
            WHERE link.project_id = #{projectId}
              AND link.business_type = 'PROJECT_DOCUMENT'
              AND asset.deleted_at IS NULL
            <if test='keyword != null'>
              AND LOWER(asset.original_name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY asset.created_at DESC, asset.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FileAsset> selectProjectFiles(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT asset.id, asset.file_group_id, asset.version, asset.original_name,
                   asset.storage_key, asset.media_type, asset.size_bytes, asset.sha256,
                   asset.status, asset.uploaded_by,
                   uploader.display_name AS uploaded_by_display_name,
                   asset.created_at, asset.updated_at, asset.deleted_at
            FROM file_asset asset
            JOIN app_user uploader ON uploader.id = asset.uploaded_by
            WHERE asset.id = #{fileAssetId} AND asset.deleted_at IS NULL
            """)
    FileAsset selectResponseById(@Param("fileAssetId") Long fileAssetId);

    @Select("""
            SELECT asset.id, asset.file_group_id, asset.version, asset.original_name,
                   asset.storage_key, asset.media_type, asset.size_bytes, asset.sha256,
                   asset.status, asset.uploaded_by,
                   uploader.display_name AS uploaded_by_display_name,
                   asset.created_at, asset.updated_at, asset.deleted_at
            FROM file_asset asset
            JOIN file_link link ON link.file_asset_id = asset.id
            JOIN app_user uploader ON uploader.id = asset.uploaded_by
            WHERE asset.id = #{fileAssetId}
              AND link.project_id = #{projectId}
              AND asset.deleted_at IS NULL
            """)
    FileAsset selectProjectFile(
            @Param("projectId") Long projectId,
            @Param("fileAssetId") Long fileAssetId);

    @Select("""
            SELECT asset.id, asset.file_group_id, asset.version, asset.original_name,
                   asset.storage_key, asset.media_type, asset.size_bytes, asset.sha256,
                   asset.status, asset.uploaded_by,
                   uploader.display_name AS uploaded_by_display_name,
                   asset.created_at, asset.updated_at, asset.deleted_at
            FROM file_asset asset
            JOIN file_link link ON link.file_asset_id = asset.id
            JOIN app_user uploader ON uploader.id = asset.uploaded_by
            WHERE asset.file_group_id = CAST(#{fileGroupId} AS uuid)
              AND link.project_id = #{projectId}
              AND asset.deleted_at IS NULL
            ORDER BY asset.version DESC
            LIMIT 1
            """)
    FileAsset selectLatestVersion(
            @Param("projectId") Long projectId,
            @Param("fileGroupId") String fileGroupId);

    @Select("""
            SELECT asset.id, asset.file_group_id, asset.version, asset.original_name,
                   asset.storage_key, asset.media_type, asset.size_bytes, asset.sha256,
                   asset.status, asset.uploaded_by,
                   uploader.display_name AS uploaded_by_display_name,
                   asset.created_at, asset.updated_at, asset.deleted_at
            FROM file_asset asset
            JOIN file_link link ON link.file_asset_id = asset.id
            JOIN app_user uploader ON uploader.id = asset.uploaded_by
            WHERE asset.file_group_id = CAST(#{fileGroupId} AS uuid)
              AND link.project_id = #{projectId}
              AND asset.deleted_at IS NULL
            ORDER BY asset.version DESC
            """)
    List<FileAsset> selectVersions(
            @Param("projectId") Long projectId,
            @Param("fileGroupId") String fileGroupId);

    @Select("""
            SELECT file_asset_id, chunk_index, size_bytes, sha256
            FROM file_upload_chunk
            WHERE file_asset_id = #{fileAssetId} AND chunk_index = #{chunkIndex}
            """)
    FileUploadChunk selectChunk(
            @Param("fileAssetId") Long fileAssetId,
            @Param("chunkIndex") int chunkIndex);

    @Insert("""
            INSERT INTO file_upload_chunk (file_asset_id, chunk_index, size_bytes, sha256)
            VALUES (#{fileAssetId}, #{chunkIndex}, #{sizeBytes}, #{sha256})
            """)
    int insertChunk(
            @Param("fileAssetId") Long fileAssetId,
            @Param("chunkIndex") int chunkIndex,
            @Param("sizeBytes") int sizeBytes,
            @Param("sha256") String sha256);

    @Select("""
            SELECT count(*)
            FROM file_upload_chunk
            WHERE file_asset_id = #{fileAssetId}
            """)
    int countChunks(@Param("fileAssetId") Long fileAssetId);

    @Update("""
            UPDATE file_asset
            SET status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{fileAssetId}
              AND status IN ('RESERVED', 'UPLOADING')
              AND deleted_at IS NULL
            """)
    int updateStatus(
            @Param("fileAssetId") Long fileAssetId,
            @Param("status") FileStatus status);

    @Update("""
            UPDATE file_asset
            SET status = 'AVAILABLE', sha256 = #{sha256}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{fileAssetId}
              AND status IN ('RESERVED', 'UPLOADING')
              AND deleted_at IS NULL
            """)
    int markAvailable(
            @Param("fileAssetId") Long fileAssetId,
            @Param("sha256") String sha256);

    @Update("""
            UPDATE file_asset
            SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP
            WHERE id = #{fileAssetId}
              AND status IN ('RESERVED', 'UPLOADING')
              AND deleted_at IS NULL
            """)
    int markUploadCancelled(@Param("fileAssetId") Long fileAssetId);

    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM file_upload_chunk WHERE file_asset_id = #{fileAssetId}
            """)
    int deleteChunks(@Param("fileAssetId") Long fileAssetId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, 'FILE_METADATA_RESERVED', 'FILE_ASSET', #{fileAssetId},
                'SUCCESS', jsonb_build_object('projectId', #{projectId}, 'originalName', #{originalName})
            )
            """)
    int recordReservedAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("fileAssetId") String fileAssetId,
            @Param("projectId") Long projectId,
            @Param("originalName") String originalName);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, #{action}, 'FILE_ASSET', #{fileAssetId}, #{outcome},
                jsonb_build_object('projectId', #{projectId})
            )
            """)
    int recordFileAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("fileAssetId") String fileAssetId,
            @Param("projectId") Long projectId,
            @Param("outcome") String outcome);
}
