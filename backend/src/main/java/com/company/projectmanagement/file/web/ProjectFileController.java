package com.company.projectmanagement.file.web;

import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.file.service.ProjectFileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/files")
public class ProjectFileController {

    private final ProjectFileService projectFileService;

    public ProjectFileController(ProjectFileService projectFileService) {
        this.projectFileService = projectFileService;
    }

    @GetMapping
    public PageResponse<FileAssetResponse> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            Principal principal) {
        return projectFileService.list(projectId, page, pageSize, keyword, principal.getName());
    }

    @PostMapping("/metadata")
    public ResponseEntity<FileAssetResponse> reserve(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateFileMetadataRequest request,
            Principal principal) {
        FileAssetResponse response = projectFileService.reserve(projectId, request, principal.getName());
        return ResponseEntity.created(URI.create(
                        "/api/v1/projects/%d/files/%d".formatted(projectId, response.id())))
                .body(response);
    }

    @PutMapping(
            value = "/{fileId}/chunks/{chunkIndex}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> uploadChunk(
            @PathVariable Long projectId,
            @PathVariable Long fileId,
            @PathVariable @Min(0) int chunkIndex,
            HttpServletRequest request,
            Principal principal) throws java.io.IOException {
        projectFileService.uploadChunk(
                projectId,
                fileId,
                chunkIndex,
                request.getContentLengthLong(),
                request.getInputStream(),
                principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{fileId}/complete")
    public FileAssetResponse complete(
            @PathVariable Long projectId,
            @PathVariable Long fileId,
            Principal principal) {
        return projectFileService.complete(projectId, fileId, principal.getName());
    }

    @DeleteMapping("/{fileId}/upload")
    public ResponseEntity<Void> cancelUpload(
            @PathVariable Long projectId,
            @PathVariable Long fileId,
            Principal principal) {
        projectFileService.cancelUpload(projectId, fileId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/groups/{fileGroupId}/versions")
    public List<FileAssetResponse> versions(
            @PathVariable Long projectId,
            @PathVariable String fileGroupId,
            Principal principal) {
        return projectFileService.versions(projectId, fileGroupId, principal.getName());
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable Long projectId,
            @PathVariable Long fileId,
            Principal principal) throws java.io.IOException {
        ProjectFileService.DownloadFile download = projectFileService.download(
                projectId, fileId, principal.getName());
        MediaType mediaType;
        try {
            mediaType = download.asset().getMediaType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(download.asset().getMediaType());
        } catch (org.springframework.http.InvalidMediaTypeException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.asset().getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(java.nio.file.Files.size(download.path()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(download.path()));
    }
}
