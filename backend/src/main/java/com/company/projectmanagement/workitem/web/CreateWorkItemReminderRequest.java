package com.company.projectmanagement.workitem.web;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateWorkItemReminderRequest(
        @NotNull @Positive Long workItemId,
        @NotNull @Future OffsetDateTime remindAt,
        @Size(max = 500) String message) {
}
