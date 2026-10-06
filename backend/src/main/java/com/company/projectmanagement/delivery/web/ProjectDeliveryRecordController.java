package com.company.projectmanagement.delivery.web;

import com.company.projectmanagement.delivery.service.ProjectDeliveryRecordService;
import com.company.projectmanagement.delivery.service.ProjectDeliveryRecordService.DesignInput;
import com.company.projectmanagement.delivery.service.ProjectDeliveryRecordService.InterfaceInput;
import com.company.projectmanagement.delivery.service.ProjectDeliveryRecordService.MeetingInput;
import com.company.projectmanagement.delivery.service.ProjectDeliveryRecordService.VendorInput;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectDeliveryRecordController {
    private final ProjectDeliveryRecordService service;

    public ProjectDeliveryRecordController(ProjectDeliveryRecordService service) {
        this.service = service;
    }

    @GetMapping("/vendors")
    public List<Map<String, Object>> vendors(@PathVariable long projectId, Principal principal) {
        return service.listVendors(projectId, principal.getName());
    }

    @PostMapping("/vendors")
    public ResponseEntity<Map<String, Object>> createVendor(
            @PathVariable long projectId, @Valid @RequestBody VendorInput input, Principal principal) {
        Map<String, Object> result = service.createVendor(projectId, input, principal.getName());
        return created(projectId, "vendors", result);
    }

    @GetMapping("/interfaces")
    public List<Map<String, Object>> interfaces(@PathVariable long projectId, Principal principal) {
        return service.listInterfaces(projectId, principal.getName());
    }

    @PostMapping("/interfaces")
    public ResponseEntity<Map<String, Object>> createInterface(
            @PathVariable long projectId, @Valid @RequestBody InterfaceInput input, Principal principal) {
        Map<String, Object> result = service.createInterface(projectId, input, principal.getName());
        return created(projectId, "interfaces", result);
    }

    @PostMapping("/interfaces/{id}/submit")
    public Map<String, Object> submitInterface(
            @PathVariable long projectId, @PathVariable long id, Principal principal) {
        return service.submitInterface(projectId, id, principal.getName());
    }

    @GetMapping("/meeting-records")
    public List<Map<String, Object>> meetings(@PathVariable long projectId, Principal principal) {
        return service.listMeetings(projectId, principal.getName());
    }

    @PostMapping("/meeting-records")
    public ResponseEntity<Map<String, Object>> createMeeting(
            @PathVariable long projectId, @Valid @RequestBody MeetingInput input, Principal principal) {
        Map<String, Object> result = service.createMeeting(projectId, input, principal.getName());
        return created(projectId, "meeting-records", result);
    }

    @PostMapping("/meeting-records/{id}/submit")
    public Map<String, Object> submitMeeting(
            @PathVariable long projectId, @PathVariable long id, Principal principal) {
        return service.submitMeeting(projectId, id, principal.getName());
    }

    @GetMapping("/design-assets")
    public List<Map<String, Object>> designs(@PathVariable long projectId, Principal principal) {
        return service.listDesigns(projectId, principal.getName());
    }

    @PostMapping("/design-assets")
    public ResponseEntity<Map<String, Object>> createDesign(
            @PathVariable long projectId, @Valid @RequestBody DesignInput input, Principal principal) {
        Map<String, Object> result = service.createDesign(projectId, input, principal.getName());
        return created(projectId, "design-assets", result);
    }

    private static ResponseEntity<Map<String, Object>> created(
            long projectId, String segment, Map<String, Object> result) {
        URI location = URI.create("/api/v1/projects/" + projectId + "/" + segment + "/" + result.get("id"));
        return ResponseEntity.created(location).body(result);
    }
}
