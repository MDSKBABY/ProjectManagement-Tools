package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemStatusLog;
import java.time.OffsetDateTime;

/** 状态历史响应保留当时操作人与备注。 */
public record WorkItemStatusLogResponse(
        Long id,
        WorkItemStatus fromStatus,
        WorkItemStatus toStatus,
        String comment,
        ChangedByResponse changedBy,
        OffsetDateTime changedAt) {

    public static WorkItemStatusLogResponse from(WorkItemStatusLog log) {
        return new WorkItemStatusLogResponse(
                log.getId(), log.getFromStatus(), log.getToStatus(), log.getComment(),
                new ChangedByResponse(log.getChangedBy(), log.getChangedByDisplayName()),
                log.getChangedAt());
    }

    public record ChangedByResponse(Long id, String displayName) {
    }
}
