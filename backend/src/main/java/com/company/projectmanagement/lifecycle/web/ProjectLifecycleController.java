package com.company.projectmanagement.lifecycle.web;

import com.company.projectmanagement.lifecycle.service.ProjectLifecycleService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/lifecycle")
public class ProjectLifecycleController {
    private final ProjectLifecycleService service;

    public ProjectLifecycleController(ProjectLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    public List<LifecycleNodeResponse> list(
            @PathVariable long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal) {
        return service.list(projectId, from, to, principal.getName());
    }
}
