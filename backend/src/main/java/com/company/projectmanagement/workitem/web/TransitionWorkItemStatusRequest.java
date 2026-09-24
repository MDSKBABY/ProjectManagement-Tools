package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 状态流转独立于普通编辑，保证每次变更都可追溯。 */
public record TransitionWorkItemStatusRequest(
        @NotNull WorkItemStatus status,
        @Size(max = 1000) String comment) {
}
