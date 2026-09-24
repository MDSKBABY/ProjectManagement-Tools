package com.company.projectmanagement.workitem.web;

import com.company.projectmanagement.workitem.domain.WorkItemPriority;
import com.company.projectmanagement.workitem.domain.WorkItemType;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PATCH 请求显式记录字段是否出现，因此可区分“未修改”与“清空为 null”。
 */
public class UpdateWorkItemRequest {
    private WorkItemType type;
    private boolean typePresent;
    private String title;
    private boolean titlePresent;
    @Size(max = 10_000)
    private String description;
    private boolean descriptionPresent;
    private WorkItemPriority priority;
    private boolean priorityPresent;
    private Long assigneeId;
    private boolean assigneeIdPresent;
    private LocalDate plannedStartDate;
    private boolean plannedStartDatePresent;
    private LocalDate plannedEndDate;
    private boolean plannedEndDatePresent;

    public WorkItemType getType() { return type; }
    public boolean isTypePresent() { return typePresent; }
    @JsonSetter("type")
    public void setType(WorkItemType value) { type = value; typePresent = true; }

    public String getTitle() { return title; }
    public boolean isTitlePresent() { return titlePresent; }
    @JsonSetter("title")
    public void setTitle(String value) { title = value; titlePresent = true; }

    public String getDescription() { return description; }
    public boolean isDescriptionPresent() { return descriptionPresent; }
    @JsonSetter("description")
    public void setDescription(String value) { description = value; descriptionPresent = true; }

    public WorkItemPriority getPriority() { return priority; }
    public boolean isPriorityPresent() { return priorityPresent; }
    @JsonSetter("priority")
    public void setPriority(WorkItemPriority value) { priority = value; priorityPresent = true; }

    public Long getAssigneeId() { return assigneeId; }
    public boolean isAssigneeIdPresent() { return assigneeIdPresent; }
    @JsonSetter("assigneeId")
    public void setAssigneeId(Long value) { assigneeId = value; assigneeIdPresent = true; }

    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public boolean isPlannedStartDatePresent() { return plannedStartDatePresent; }
    @JsonSetter("plannedStartDate")
    public void setPlannedStartDate(LocalDate value) {
        plannedStartDate = value;
        plannedStartDatePresent = true;
    }

    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public boolean isPlannedEndDatePresent() { return plannedEndDatePresent; }
    @JsonSetter("plannedEndDate")
    public void setPlannedEndDate(LocalDate value) {
        plannedEndDate = value;
        plannedEndDatePresent = true;
    }

    public boolean hasChanges() {
        return typePresent || titlePresent || descriptionPresent || priorityPresent
                || assigneeIdPresent || plannedStartDatePresent || plannedEndDatePresent;
    }
}
