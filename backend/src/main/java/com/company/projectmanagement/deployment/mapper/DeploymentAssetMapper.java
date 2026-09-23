package com.company.projectmanagement.deployment.mapper;

import com.company.projectmanagement.deployment.domain.DeploymentAsset;
import com.company.projectmanagement.deployment.domain.DeploymentAssetType;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.deployment.domain.RiskLevel;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 部署资产查询始终限制项目，文件关联只允许指向该项目内的可用文件。 */
@Mapper
public interface DeploymentAssetMapper {

    String RESPONSE_COLUMNS = """
            asset.id, asset.asset_group_id, asset.version, asset.project_id,
            asset.name, asset.asset_type, asset.version_label, asset.file_asset_id,
            asset.operating_system, asset.architecture, asset.environment, asset.risk_level,
            asset.tags::text AS tags_json, asset.description, asset.prerequisites,
            asset.execution_instructions, asset.rollback_instructions,
            asset.created_by, creator.display_name AS created_by_display_name, asset.created_at,
            file.original_name AS file_original_name, file.size_bytes AS file_size_bytes,
            file.status AS file_status
            """;

    @Insert("""
            INSERT INTO deployment_asset (
                asset_group_id, version, project_id, name, asset_type, version_label,
                file_asset_id, operating_system, architecture, environment, risk_level,
                tags, description, prerequisites, execution_instructions,
                rollback_instructions, created_by
            ) VALUES (
                CAST(#{assetGroupId} AS uuid), #{version}, #{projectId}, #{name}, #{assetType},
                #{versionLabel}, #{fileAssetId}, #{operatingSystem}, #{architecture},
                #{environment}, #{riskLevel}, CAST(#{tagsJson} AS jsonb), #{description},
                #{prerequisites}, #{executionInstructions}, #{rollbackInstructions}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeploymentAsset asset);

    @Insert("""
            INSERT INTO file_link (
                file_asset_id, project_id, business_type, business_id, created_by
            ) VALUES (
                #{fileAssetId}, #{projectId}, 'DEPLOYMENT_ASSET', #{assetId}, #{createdBy}
            )
            """)
    int linkFile(
            @Param("fileAssetId") Long fileAssetId,
            @Param("projectId") Long projectId,
            @Param("assetId") Long assetId,
            @Param("createdBy") Long createdBy);

    @Select("""
            <script>
            SELECT count(*) FROM deployment_asset asset
            WHERE asset.project_id = #{projectId}
            <if test='keyword != null'>
              AND (LOWER(asset.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(asset.version_label) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='assetType != null'>AND asset.asset_type = #{assetType}</if>
            <if test='operatingSystem != null'>
              AND LOWER(asset.operating_system) = LOWER(#{operatingSystem})
            </if>
            <if test='architecture != null'>
              AND LOWER(asset.architecture) = LOWER(#{architecture})
            </if>
            <if test='environment != null'>AND asset.environment = #{environment}</if>
            <if test='riskLevel != null'>AND asset.risk_level = #{riskLevel}</if>
            <if test='tag != null'>AND jsonb_exists(asset.tags, #{tag})</if>
            </script>
            """)
    long count(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("assetType") DeploymentAssetType assetType,
            @Param("operatingSystem") String operatingSystem,
            @Param("architecture") String architecture,
            @Param("environment") DeploymentEnvironment environment,
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("tag") String tag);

    @Select("""
            <script>
            SELECT
            """ + RESPONSE_COLUMNS + """
            FROM deployment_asset asset
            JOIN app_user creator ON creator.id = asset.created_by
            JOIN file_asset file ON file.id = asset.file_asset_id
            WHERE asset.project_id = #{projectId}
            <if test='keyword != null'>
              AND (LOWER(asset.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(asset.version_label) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='assetType != null'>AND asset.asset_type = #{assetType}</if>
            <if test='operatingSystem != null'>
              AND LOWER(asset.operating_system) = LOWER(#{operatingSystem})
            </if>
            <if test='architecture != null'>
              AND LOWER(asset.architecture) = LOWER(#{architecture})
            </if>
            <if test='environment != null'>AND asset.environment = #{environment}</if>
            <if test='riskLevel != null'>AND asset.risk_level = #{riskLevel}</if>
            <if test='tag != null'>AND jsonb_exists(asset.tags, #{tag})</if>
            ORDER BY asset.created_at DESC, asset.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DeploymentAsset> selectPage(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword,
            @Param("assetType") DeploymentAssetType assetType,
            @Param("operatingSystem") String operatingSystem,
            @Param("architecture") String architecture,
            @Param("environment") DeploymentEnvironment environment,
            @Param("riskLevel") RiskLevel riskLevel,
            @Param("tag") String tag,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT
            """ + RESPONSE_COLUMNS + """
            FROM deployment_asset asset
            JOIN app_user creator ON creator.id = asset.created_by
            JOIN file_asset file ON file.id = asset.file_asset_id
            WHERE asset.project_id = #{projectId} AND asset.id = #{assetId}
            """)
    DeploymentAsset selectById(
            @Param("projectId") Long projectId,
            @Param("assetId") Long assetId);

    @Select("""
            SELECT
            """ + RESPONSE_COLUMNS + """
            FROM deployment_asset asset
            JOIN app_user creator ON creator.id = asset.created_by
            JOIN file_asset file ON file.id = asset.file_asset_id
            WHERE asset.project_id = #{projectId}
              AND asset.asset_group_id = CAST(#{assetGroupId} AS uuid)
            ORDER BY asset.version DESC
            """)
    List<DeploymentAsset> selectVersions(
            @Param("projectId") Long projectId,
            @Param("assetGroupId") String assetGroupId);

    @Select("""
            SELECT asset.id, asset.asset_group_id, asset.version, asset.project_id
            FROM deployment_asset asset
            WHERE asset.project_id = #{projectId}
              AND asset.asset_group_id = CAST(#{assetGroupId} AS uuid)
            ORDER BY asset.version DESC
            LIMIT 1
            """)
    DeploymentAsset selectLatestVersion(
            @Param("projectId") Long projectId,
            @Param("assetGroupId") String assetGroupId);

    @Insert("""
            INSERT INTO audit_log (
                actor_user_id, action, resource_type, resource_id, outcome, details
            ) VALUES (
                #{actorUserId}, 'DEPLOYMENT_ASSET_CREATED', 'DEPLOYMENT_ASSET',
                #{assetId}, 'SUCCESS',
                jsonb_build_object('projectId', #{projectId}, 'version', #{version})
            )
            """)
    int recordCreatedAudit(
            @Param("actorUserId") Long actorUserId,
            @Param("assetId") String assetId,
            @Param("projectId") Long projectId,
            @Param("version") int version);
}
