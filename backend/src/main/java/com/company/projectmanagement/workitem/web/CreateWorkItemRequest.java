package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemPriority;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 创建工作项时的输入边界，状态由服务端统一初始化。 */
public record CreateWorkItemRequest(
        @NotNull WorkItemType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10_000) String description,
        WorkItemPriority priority,
        @Positive Long assigneeId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate) {
}
