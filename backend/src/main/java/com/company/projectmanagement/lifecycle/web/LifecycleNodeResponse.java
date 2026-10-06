package com.company.projectmanagement.lifecycle.web;

import java.time.LocalDate;

public record LifecycleNodeResponse(
        String type, Long referenceId, String title, LocalDate date,
        String scheduleStatus, String businessStatus, String description) { }
