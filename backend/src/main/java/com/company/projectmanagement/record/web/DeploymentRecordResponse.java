package com.company.projectmanagement.record.web;

import com.company.projectmanagement.record.domain.DeploymentRecord;
import com.company.projectmanagement.record.domain.DeploymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

/** 部署记录详情始终读取保存的快照，避免源方案修改后污染历史。 */
public record DeploymentRecordResponse(
        Long id,
        Long projectId,
        Long serverId,
        String serverName,
        Long solutionId,
        String solutionName,
        Long environmentFingerprintId,
        DeploymentResult result,
        Long executedBy,
        String executedByName,
        OffsetDateTime executedAt,
        String exceptionNotes,
        String notes,
        boolean baseline,
        JsonNode serverSnapshot,
        JsonNode environmentSnapshot,
        JsonNode solutionSnapshot,
        OffsetDateTime createdAt) {

    public static DeploymentRecordResponse from(DeploymentRecord record, ObjectMapper objectMapper) {
        return new DeploymentRecordResponse(
                record.getId(), record.getProjectId(), record.getServerId(), record.getServerName(),
                record.getSolutionId(), record.getSolutionName(), record.getEnvironmentFingerprintId(),
                record.getResult(),
                record.getExecutedBy(), record.getExecutedByName(), record.getExecutedAt(),
                record.getExceptionNotes(), record.getNotes(), Boolean.TRUE.equals(record.getBaseline()),
                read(objectMapper, record.getServerSnapshotJson()),
                read(objectMapper, record.getEnvironmentSnapshotJson()),
                read(objectMapper, record.getSolutionSnapshotJson()), record.getCreatedAt());
    }

    private static JsonNode read(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取部署记录快照", exception);
        }
    }
}
