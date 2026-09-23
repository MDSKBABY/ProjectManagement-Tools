package com.company.projectmanagement.file.service;

import com.company.projectmanagement.common.web.ApiException;
import com.company.projectmanagement.common.web.PageResponse;
import com.company.projectmanagement.file.domain.FileAsset;
import com.company.projectmanagement.file.domain.FileStatus;
import com.company.projectmanagement.file.domain.FileUploadChunk;
import com.company.projectmanagement.file.mapper.FileAssetMapper;
import com.company.projectmanagement.file.storage.LocalFileStorage;
import com.company.projectmanagement.file.web.CreateFileMetadataRequest;
import com.company.projectmanagement.file.web.FileAssetResponse;
import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.mapper.AppUserMapper;
import com.company.projectmanagement.identity.mapper.IdentityAccessMapper;
import com.company.projectmanagement.project.domain.Project;
import com.company.projectmanagement.project.mapper.ProjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** 项目资料服务，统一处理项目可见性、可写角色和元数据事务。 */
@Service
public class ProjectFileService {

    private final FileAssetMapper fileAssetMapper;
    private final ProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final LocalFileStorage localFileStorage;
    private final TransactionTemplate transactionTemplate;
    private final int chunkSizeBytes;

    public ProjectFileService(
            FileAssetMapper fileAssetMapper,
            ProjectMapper projectMapper,
            AppUserMapper appUserMapper,
            IdentityAccessMapper identityAccessMapper,
            LocalFileStorage localFileStorage,
            TransactionTemplate transactionTemplate,
            @Value("${app.file-storage.chunk-size-bytes}") int chunkSizeBytes) {
        this.fileAssetMapper = fileAssetMapper;
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.localFileStorage = localFileStorage;
        this.transactionTemplate = transactionTemplate;
        if (chunkSizeBytes <= 0) {
            throw new IllegalStateException("文件分片大小必须大于 0");
        }
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public PageResponse<FileAssetResponse> list(
            Long projectId, int page, int pageSize, String keyword, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = fileAssetMapper.countProjectFiles(projectId, normalizedKeyword);
        var data = fileAssetMapper.selectProjectFiles(
                        projectId, normalizedKeyword, (page - 1) * pageSize, pageSize)
                .stream()
                .map(FileAssetResponse::from)
                .toList();
        return PageResponse.of(data, page, pageSize, total);
    }

    /** 预留全新文件或既有文件组的下一不可变版本。 */
    @Transactional
    public FileAssetResponse reserve(
            Long projectId, CreateFileMetadataRequest request, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        if (!actor.administrator()
                && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权维护该项目的文件");
        }

        localFileStorage.prepareRoot();
        String storageKey = localFileStorage.generateStorageKey(projectId);
        localFileStorage.resolve(storageKey);

        VersionTarget versionTarget = resolveVersionTarget(projectId, request.fileGroupId());
        FileAsset asset = new FileAsset();
        asset.setFileGroupId(versionTarget.fileGroupId());
        asset.setVersion(versionTarget.version());
        asset.setOriginalName(request.originalName().trim());
        asset.setStorageKey(storageKey);
        asset.setMediaType(normalizeNullable(request.mediaType()));
        asset.setSizeBytes(request.sizeBytes());
        asset.setSha256(normalizeSha256(request.sha256()));
        asset.setStatus(FileStatus.RESERVED);
        asset.setUploadedBy(actor.user().getId());
        fileAssetMapper.insert(asset);
        fileAssetMapper.linkProjectDocument(
                asset.getId(), projectId, actor.user().getId());
        fileAssetMapper.recordReservedAudit(
                actor.user().getId(), asset.getId().toString(), projectId, asset.getOriginalName());
        return FileAssetResponse.forUpload(
                fileAssetMapper.selectResponseById(asset.getId()),
                chunkSizeBytes,
                totalChunks(asset.getSizeBytes()));
    }

    public void uploadChunk(
            Long projectId,
            Long fileId,
            int chunkIndex,
            long contentLength,
            InputStream input,
            String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        FileAsset asset = requireProjectFile(projectId, fileId);
        if (asset.getStatus() != FileStatus.RESERVED && asset.getStatus() != FileStatus.UPLOADING) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FILE_UPLOAD_NOT_OPEN", "该文件当前不能接收分片");
        }

        int totalChunks = totalChunks(asset.getSizeBytes());
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UPLOAD_CHUNK_OUT_OF_RANGE",
                    "文件分片序号超出范围");
        }
        long expectedSize = expectedChunkSize(asset.getSizeBytes(), chunkIndex);
        if (contentLength >= 0 && contentLength != expectedSize) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UPLOAD_CHUNK_SIZE_INVALID",
                    "文件分片大小不正确");
        }

        LocalFileStorage.StagedContent staged = localFileStorage.stageChunk(
                asset.getStorageKey(), chunkIndex, input, expectedSize);
        FileUploadChunk existing = fileAssetMapper.selectChunk(fileId, chunkIndex);
        if (existing != null) {
            if (!sameChunk(existing, staged)) {
                localFileStorage.discard(staged);
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "UPLOAD_CHUNK_CONFLICT",
                        "该分片已上传且内容不同");
            }
            if (localFileStorage.chunkExists(asset.getStorageKey(), chunkIndex)) {
                localFileStorage.discard(staged);
            } else {
                localFileStorage.publish(staged);
            }
            return;
        }

        try {
            fileAssetMapper.insertChunk(
                    fileId, chunkIndex, Math.toIntExact(staged.sizeBytes()), staged.sha256());
        } catch (DuplicateKeyException exception) {
            FileUploadChunk concurrent = fileAssetMapper.selectChunk(fileId, chunkIndex);
            if (concurrent == null || !sameChunk(concurrent, staged)) {
                localFileStorage.discard(staged);
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "UPLOAD_CHUNK_CONFLICT",
                        "该分片已上传且内容不同");
            }
        }
        localFileStorage.publish(staged);
        fileAssetMapper.updateStatus(fileId, FileStatus.UPLOADING);
    }

    /** 先在临时文件校验完整内容，再原子移动并用数据库事务发布元数据。 */
    public FileAssetResponse complete(Long projectId, Long fileId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        FileAsset asset = requireProjectFile(projectId, fileId);
        if (asset.getStatus() == FileStatus.AVAILABLE) {
            return FileAssetResponse.from(asset);
        }
        if (asset.getStatus() != FileStatus.RESERVED && asset.getStatus() != FileStatus.UPLOADING) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FILE_UPLOAD_NOT_OPEN", "该文件当前不能完成上传");
        }

        int totalChunks = totalChunks(asset.getSizeBytes());
        if (fileAssetMapper.countChunks(fileId) != totalChunks) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "UPLOAD_CHUNKS_INCOMPLETE",
                    "文件分片尚未上传完整");
        }
        LocalFileStorage.StagedContent merged = localFileStorage.mergeChunks(
                asset.getStorageKey(), totalChunks, asset.getSizeBytes());
        if (asset.getSha256() != null && !asset.getSha256().equals(merged.sha256())) {
            localFileStorage.discard(merged);
            fileAssetMapper.updateStatus(fileId, FileStatus.FAILED);
            fileAssetMapper.recordFileAudit(
                    actor.user().getId(),
                    "FILE_UPLOAD_CHECKSUM_FAILED",
                    fileId.toString(),
                    projectId,
                    "FAILURE");
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "FILE_CHECKSUM_MISMATCH",
                    "文件完整性校验失败");
        }

        localFileStorage.publish(merged);
        transactionTemplate.executeWithoutResult(status -> {
            if (fileAssetMapper.markAvailable(fileId, merged.sha256()) == 0) {
                throw new ApiException(
                        HttpStatus.CONFLICT, "FILE_UPLOAD_NOT_OPEN", "该文件当前不能完成上传");
            }
            fileAssetMapper.deleteChunks(fileId);
            fileAssetMapper.recordFileAudit(
                    actor.user().getId(),
                    "FILE_UPLOAD_COMPLETED",
                    fileId.toString(),
                    projectId,
                    "SUCCESS");
        });
        try {
            localFileStorage.cleanupChunks(asset.getStorageKey(), totalChunks);
        } catch (ApiException ignored) {
            // 已发布文件和数据库状态是一致的；残留分片可由后续维护任务清理。
        }
        return FileAssetResponse.from(fileAssetMapper.selectResponseById(fileId));
    }

    public DownloadFile download(Long projectId, Long fileId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        FileAsset asset = requireProjectFile(projectId, fileId);
        if (asset.getStatus() != FileStatus.AVAILABLE) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FILE_NOT_AVAILABLE", "文件尚不可下载");
        }
        Path path = localFileStorage.availableFile(asset.getStorageKey());
        if (!Files.isRegularFile(path)) {
            fileAssetMapper.updateStatus(fileId, FileStatus.FAILED);
            fileAssetMapper.recordFileAudit(
                    actor.user().getId(),
                    "FILE_CONTENT_MISSING",
                    fileId.toString(),
                    projectId,
                    "FAILURE");
            throw new ApiException(
                    HttpStatus.GONE, "FILE_CONTENT_MISSING", "文件内容已丢失，请联系管理员");
        }
        fileAssetMapper.recordFileAudit(
                actor.user().getId(),
                "FILE_DOWNLOADED",
                fileId.toString(),
                projectId,
                "SUCCESS");
        return new DownloadFile(asset, path);
    }

    public void cancelUpload(Long projectId, Long fileId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireWritableProject(projectId, actor);
        FileAsset asset = requireProjectFile(projectId, fileId);
        if (asset.getStatus() == FileStatus.AVAILABLE) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FILE_ALREADY_AVAILABLE", "已完成文件不能取消上传");
        }
        transactionTemplate.executeWithoutResult(status -> {
            fileAssetMapper.markUploadCancelled(fileId);
            fileAssetMapper.deleteChunks(fileId);
            fileAssetMapper.recordFileAudit(
                    actor.user().getId(),
                    "FILE_UPLOAD_CANCELLED",
                    fileId.toString(),
                    projectId,
                    "SUCCESS");
        });
        localFileStorage.cleanupUpload(asset.getStorageKey());
    }

    public java.util.List<FileAssetResponse> versions(
            Long projectId, String fileGroupId, String actorUsername) {
        Actor actor = requireActor(actorUsername);
        requireVisibleProject(projectId, actor);
        validateGroupId(fileGroupId);
        var versions = fileAssetMapper.selectVersions(projectId, fileGroupId)
                .stream()
                .map(FileAssetResponse::from)
                .toList();
        if (versions.isEmpty()) {
            throw fileNotFound();
        }
        return versions;
    }

    private VersionTarget resolveVersionTarget(Long projectId, String requestedGroupId) {
        if (!StringUtils.hasText(requestedGroupId)) {
            return new VersionTarget(UUID.randomUUID().toString(), 1);
        }
        String groupId = requestedGroupId.trim();
        validateGroupId(groupId);
        FileAsset latest = fileAssetMapper.selectLatestVersion(projectId, groupId);
        if (latest == null) {
            throw fileNotFound();
        }
        if (latest.getStatus() != FileStatus.AVAILABLE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FILE_VERSION_SOURCE_NOT_AVAILABLE",
                    "上一版本完成上传后才能创建新版本");
        }
        return new VersionTarget(groupId, latest.getVersion() + 1);
    }

    private void requireWritableProject(Long projectId, Actor actor) {
        Project project = projectMapper.selectByIdIncludingDeleted(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw projectNotFound();
        }
        if (!actor.administrator()
                && !projectMapper.canWriteFiles(projectId, actor.user().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "无权维护该项目的文件");
        }
    }

    private FileAsset requireProjectFile(Long projectId, Long fileId) {
        FileAsset asset = fileAssetMapper.selectProjectFile(projectId, fileId);
        if (asset == null) {
            throw fileNotFound();
        }
        return asset;
    }

    private int totalChunks(long sizeBytes) {
        return Math.toIntExact((sizeBytes + chunkSizeBytes - 1L) / chunkSizeBytes);
    }

    private long expectedChunkSize(long totalSize, int chunkIndex) {
        return Math.min(chunkSizeBytes, totalSize - (long) chunkIndex * chunkSizeBytes);
    }

    private static boolean sameChunk(
            FileUploadChunk existing, LocalFileStorage.StagedContent staged) {
        return existing.getSizeBytes() == staged.sizeBytes()
                && existing.getSha256().equals(staged.sha256());
    }

    private static void validateGroupId(String fileGroupId) {
        try {
            UUID.fromString(fileGroupId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "FILE_GROUP_ID_INVALID", "文件版本组标识格式不正确");
        }
    }

    private void requireVisibleProject(Long projectId, Actor actor) {
        if (projectMapper.selectVisibleById(
                projectId, actor.user().getId(), actor.administrator()) == null) {
            throw projectNotFound();
        }
    }

    private Actor requireActor(String username) {
        AppUser user = appUserMapper.selectActiveByUsername(username);
        if (user == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问");
        }
        boolean administrator = identityAccessMapper.selectRoleCodesByUserId(user.getId())
                .contains("ADMIN");
        return new Actor(user, administrator);
    }

    private static String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeSha256(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static ApiException projectNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在");
    }

    private static ApiException fileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在");
    }

    public record DownloadFile(FileAsset asset, Path path) {
    }

    private record VersionTarget(String fileGroupId, int version) {
    }

    private record Actor(AppUser user, boolean administrator) {
    }
}
