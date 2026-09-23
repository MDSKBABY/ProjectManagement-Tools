package com.company.projectmanagement.environment.web;

import com.company.projectmanagement.deployment.domain.DeploymentEnvironment;
import com.company.projectmanagement.environment.domain.EnvironmentFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;

public record EnvironmentFingerprintResponse(
        Long id,
        Long projectId,
        String name,
        DeploymentEnvironment environment,
        String operatingSystem,
        String osVersion,
        String kernelVersion,
        String architecture,
        String runtimeName,
        String runtimeVersion,
        String databaseName,
        String databaseVersion,
        List<String> middlewares,
        String networkZone,
        List<String> tags,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static EnvironmentFingerprintResponse from(
            EnvironmentFingerprint fingerprint, ObjectMapper objectMapper) {
        return new EnvironmentFingerprintResponse(
                fingerprint.getId(), fingerprint.getProjectId(), fingerprint.getName(),
                fingerprint.getEnvironment(), fingerprint.getOperatingSystem(),
                fingerprint.getOsVersion(), fingerprint.getKernelVersion(),
                fingerprint.getArchitecture(), fingerprint.getRuntimeName(),
                fingerprint.getRuntimeVersion(), fingerprint.getDatabaseName(),
                fingerprint.getDatabaseVersion(), readList(fingerprint.getMiddlewaresJson(), objectMapper),
                fingerprint.getNetworkZone(), readList(fingerprint.getTagsJson(), objectMapper),
                fingerprint.getNotes(), fingerprint.getCreatedAt(), fingerprint.getUpdatedAt());
    }

    private static List<String> readList(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取环境指纹列表字段", exception);
        }
    }
}
