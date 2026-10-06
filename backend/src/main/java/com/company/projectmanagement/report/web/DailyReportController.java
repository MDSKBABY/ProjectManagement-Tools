package com.company.projectmanagement.report.web;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.report.service.DailyReportService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/daily-reports")
public class DailyReportController {
    private final DailyReportService service;

    public DailyReportController(DailyReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<DailyReportResponse> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long reporterId,
            Principal principal) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "开始日期不能晚于结束日期");
        }
        return service.list(projectId, from, to, reporterId, principal.getName());
    }

    @GetMapping("/{reportId}")
    public DailyReportResponse detail(
            @PathVariable Long projectId, @PathVariable Long reportId, Principal principal) {
        return service.detail(projectId, reportId, principal.getName());
    }

    @PostMapping
    public ResponseEntity<DailyReportResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveDailyReportRequest request,
            Principal principal) {
        DailyReportResponse response = service.create(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/daily-reports/" + response.id())).body(response);
    }

    @PutMapping("/{reportId}")
    public DailyReportResponse update(
            @PathVariable Long projectId, @PathVariable Long reportId,
            @Valid @RequestBody SaveDailyReportRequest request, Principal principal) {
        return service.update(projectId, reportId, request, principal.getName());
    }

    @PostMapping("/{reportId}/confirm")
    public DailyReportResponse confirm(
            @PathVariable Long projectId, @PathVariable Long reportId, Principal principal) {
        return service.confirm(projectId, reportId, principal.getName());
    }

    @PostMapping("/{reportId}/polish")
    public DailyReportResponse polish(
            @PathVariable Long projectId, @PathVariable Long reportId, Principal principal) {
        return service.polish(projectId, reportId, principal.getName());
    }
}
