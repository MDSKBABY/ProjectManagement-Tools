package com.company.projectmanagement.record.web;

import com.company.projectmanagement.record.domain.DeploymentRecord;
import com.company.projectmanagement.record.domain.DeploymentResult;
import java.time.OffsetDateTime;

/** 列表不携带大块 JSON 快照，详情时再按需读取。 */
public record DeploymentRecordSummaryResponse(
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
        OffsetDateTime createdAt) {

    public static DeploymentRecordSummaryResponse from(DeploymentRecord record) {
        return new DeploymentRecordSummaryResponse(
                record.getId(), record.getProjectId(), record.getServerId(), record.getServerName(),
                record.getSolutionId(), record.getSolutionName(),
                record.getEnvironmentFingerprintId(), record.getResult(), record.getExecutedBy(),
                record.getExecutedByName(), record.getExecutedAt(), record.getExceptionNotes(),
                record.getNotes(), Boolean.TRUE.equals(record.getBaseline()), record.getCreatedAt());
    }
}
