package com.company.projectmanagement.report.web;

import com.company.projectmanagement.report.service.WeeklyReportService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/weekly-reports")
public class WeeklyReportController {
    private final WeeklyReportService service;
    public WeeklyReportController(WeeklyReportService service) { this.service = service; }

    @GetMapping
    public List<WeeklyReportResponse> list(@PathVariable Long projectId, Principal principal) {
        return service.list(projectId, principal.getName());
    }
    @GetMapping("/{reportId}")
    public WeeklyReportResponse detail(@PathVariable Long projectId, @PathVariable Long reportId, Principal principal) {
        return service.detail(projectId, reportId, principal.getName());
    }
    @PostMapping("/generate")
    public ResponseEntity<WeeklyReportResponse> generate(
            @PathVariable Long projectId, @Valid @RequestBody GenerateWeeklyReportRequest request,
            Principal principal) {
        WeeklyReportResponse response = service.generate(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/weekly-reports/" + response.id())).body(response);
    }
    @PutMapping("/{reportId}")
    public WeeklyReportResponse update(
            @PathVariable Long projectId, @PathVariable Long reportId,
            @Valid @RequestBody UpdateWeeklyReportRequest request, Principal principal) {
        return service.update(projectId, reportId, request, principal.getName());
    }
    @PostMapping("/{reportId}/confirm")
    public WeeklyReportResponse confirm(
            @PathVariable Long projectId, @PathVariable Long reportId, Principal principal) {
        return service.confirm(projectId, reportId, principal.getName());
    }
}
