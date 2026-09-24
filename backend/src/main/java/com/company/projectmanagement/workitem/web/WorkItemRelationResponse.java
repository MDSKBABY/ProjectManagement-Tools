package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemRelation;
import com.company.projectmanagement.workitem.domain.WorkItemRelationType;
import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import java.time.OffsetDateTime;

/** 关系响应同时返回两端摘要，避免前端为每条关系额外查询详情。 */
public record WorkItemRelationResponse(
        Long id,
        Long projectId,
        WorkItemRelationType type,
        WorkItemSummary source,
        WorkItemSummary target,
        CreatedByResponse createdBy,
        OffsetDateTime createdAt) {

    public static WorkItemRelationResponse from(WorkItemRelation relation) {
        return new WorkItemRelationResponse(
                relation.getId(),
                relation.getProjectId(),
                relation.getType(),
                new WorkItemSummary(
                        relation.getSourceWorkItemId(),
                        relation.getSourceTitle(),
                        relation.getSourceType(),
                        relation.getSourceStatus()),
                new WorkItemSummary(
                        relation.getTargetWorkItemId(),
                        relation.getTargetTitle(),
                        relation.getTargetType(),
                        relation.getTargetStatus()),
                new CreatedByResponse(
                        relation.getCreatedBy(), relation.getCreatedByDisplayName()),
                relation.getCreatedAt());
    }

    public record WorkItemSummary(
            Long id, String title, WorkItemType type, WorkItemStatus status) {
    }

    public record CreatedByResponse(Long id, String displayName) {
    }
}
