package com.company.projectmanagement.deployment.web;

import com.company.projectmanagement.deployment.domain.DeploymentAsset;
import com.company.projectmanagement.deployment.domain.DeploymentAssetType;
import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.deployment.domain.RiskLevel;
import com.company.projectmanagement.file.domain.FileStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;

public record DeploymentAssetResponse(
        Long id,
        String assetGroupId,
        int version,
        String name,
        DeploymentAssetType assetType,
        String versionLabel,
        LinkedFileResponse file,
        String operatingSystem,
        String architecture,
        DeploymentEnvironment environment,
        RiskLevel riskLevel,
        List<String> tags,
        String description,
        String prerequisites,
        String executionInstructions,
        String rollbackInstructions,
        CreatedByResponse createdBy,
        OffsetDateTime createdAt) {

    public static DeploymentAssetResponse from(DeploymentAsset asset, ObjectMapper objectMapper) {
        return new DeploymentAssetResponse(
                asset.getId(),
                asset.getAssetGroupId(),
                asset.getVersion(),
                asset.getName(),
                asset.getAssetType(),
                asset.getVersionLabel(),
                new LinkedFileResponse(
                        asset.getFileAssetId(),
                        asset.getFileOriginalName(),
                        asset.getFileSizeBytes(),
                        asset.getFileStatus()),
                asset.getOperatingSystem(),
                asset.getArchitecture(),
                asset.getEnvironment(),
                asset.getRiskLevel(),
                readTags(asset.getTagsJson(), objectMapper),
                asset.getDescription(),
                asset.getPrerequisites(),
                asset.getExecutionInstructions(),
                asset.getRollbackInstructions(),
                new CreatedByResponse(asset.getCreatedBy(), asset.getCreatedByDisplayName()),
                asset.getCreatedAt());
    }

    private static List<String> readTags(String tagsJson, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("部署资产标签数据损坏", exception);
        }
    }

    public record LinkedFileResponse(Long id, String originalName, Long sizeBytes, FileStatus status) { }
    public record CreatedByResponse(Long id, String displayName) { }
}
