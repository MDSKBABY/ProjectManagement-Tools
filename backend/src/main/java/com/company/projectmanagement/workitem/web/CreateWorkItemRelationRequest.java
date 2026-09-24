package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemRelationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 创建有向关系时，source 和 target 都必须是当前项目的未删除工作项。 */
public record CreateWorkItemRelationRequest(
        @NotNull @Positive Long sourceWorkItemId,
        @NotNull @Positive Long targetWorkItemId,
        @NotNull WorkItemRelationType type) {
}
