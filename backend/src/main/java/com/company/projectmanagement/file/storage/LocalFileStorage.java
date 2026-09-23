package com.company.projectmanagement.file.storage;

import com.company.projectmanagement.common.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 将所有文件路径限制在一个受控根目录内，业务文件名永不直接用于磁盘寻址。 */
@Component
public class LocalFileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.file-storage.root}") String configuredRoot) {
        if (!StringUtils.hasText(configuredRoot)) {
            throw new IllegalStateException("文件存储根目录不能为空");
        }
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    public String generateStorageKey(Long projectId) {
        return "projects/%d/%s".formatted(projectId, UUID.randomUUID());
    }

    /** 即使未来键来自数据库或外部请求，也不能越过配置的根目录。 */
    public Path resolve(String storageKey) {
        try {
            Path relative = Path.of(storageKey);
            if (relative.isAbsolute()) {
                throw unsafePath();
            }
            Path resolved = root.resolve(relative).normalize();
            if (!resolved.startsWith(root)) {
                throw unsafePath();
            }
            return resolved;
        } catch (InvalidPathException exception) {
            throw unsafePath();
        }
    }

    public void prepareRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建文件存储目录", exception);
        }
    }

    public StagedContent stageChunk(
            String storageKey, int chunkIndex, InputStream input, long expectedSize) {
        Path chunkDirectory = resolve(storageKey + ".upload");
        Path target = chunkDirectory.resolve("part-%08d".formatted(chunkIndex)).normalize();
        if (!target.startsWith(chunkDirectory)) {
            throw unsafePath();
        }
        try {
            Files.createDirectories(chunkDirectory);
            Path temporary = chunkDirectory.resolve(".tmp-" + UUID.randomUUID());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = 0;
            try (OutputStream output = Files.newOutputStream(
                    temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    written += read;
                    if (written > expectedSize) {
                        Files.deleteIfExists(temporary);
                        throw invalidChunkSize();
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
            if (written != expectedSize) {
                Files.deleteIfExists(temporary);
                throw invalidChunkSize();
            }
            return new StagedContent(
                    temporary, target, written, HexFormat.of().formatHex(digest.digest()));
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw storageFailure("无法写入文件分片", exception);
        }
    }

    public void publish(StagedContent staged) {
        try {
            moveReplacing(staged.temporaryPath(), staged.targetPath());
        } catch (IOException exception) {
            throw storageFailure("无法发布文件内容", exception);
        }
    }

    public void discard(StagedContent staged) {
        try {
            Files.deleteIfExists(staged.temporaryPath());
        } catch (IOException exception) {
            throw storageFailure("无法清理临时文件", exception);
        }
    }

    public boolean chunkExists(String storageKey, int chunkIndex) {
        return Files.isRegularFile(chunkPath(storageKey, chunkIndex));
    }

    public StagedContent mergeChunks(String storageKey, int totalChunks, long expectedSize) {
        Path target = resolve(storageKey);
        Path parent = target.getParent();
        try {
            Files.createDirectories(parent);
            Path temporary = parent.resolve(".merge-" + UUID.randomUUID());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = 0;
            try (OutputStream output = Files.newOutputStream(
                    temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                for (int index = 0; index < totalChunks; index++) {
                    Path part = chunkPath(storageKey, index);
                    if (!Files.isRegularFile(part)) {
                        Files.deleteIfExists(temporary);
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "UPLOAD_CHUNKS_INCOMPLETE",
                                "文件分片尚未上传完整");
                    }
                    try (InputStream input = Files.newInputStream(part)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            written += read;
                            if (written > expectedSize) {
                                Files.deleteIfExists(temporary);
                                throw invalidChunkSize();
                            }
                            output.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                        }
                    }
                }
            }
            if (written != expectedSize) {
                Files.deleteIfExists(temporary);
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "UPLOAD_SIZE_MISMATCH",
                        "合并后的文件大小不正确");
            }
            return new StagedContent(
                    temporary, target, written, HexFormat.of().formatHex(digest.digest()));
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw storageFailure("无法合并文件分片", exception);
        }
    }

    public void cleanupChunks(String storageKey, int totalChunks) {
        Path directory = resolve(storageKey + ".upload");
        try {
            for (int index = 0; index < totalChunks; index++) {
                Files.deleteIfExists(chunkPath(storageKey, index));
            }
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            throw storageFailure("无法清理已完成的文件分片", exception);
        }
    }

    /** 取消上传时清理该文件专属临时目录，不触碰其他资产。 */
    public void cleanupUpload(String storageKey) {
        Path directory = resolve(storageKey + ".upload");
        if (!Files.exists(directory)) {
            return;
        }
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                if (!child.normalize().startsWith(directory)) {
                    throw unsafePath();
                }
                Files.deleteIfExists(child);
            }
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            throw storageFailure("无法清理已取消的上传", exception);
        }
    }

    public Path availableFile(String storageKey) {
        return resolve(storageKey);
    }

    private Path chunkPath(String storageKey, int chunkIndex) {
        Path directory = resolve(storageKey + ".upload");
        Path part = directory.resolve("part-%08d".formatted(chunkIndex)).normalize();
        if (!part.startsWith(directory)) {
            throw unsafePath();
        }
        return part;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ApiException invalidChunkSize() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "UPLOAD_CHUNK_SIZE_INVALID",
                "文件分片大小不正确");
    }

    private static ApiException storageFailure(String message, Exception cause) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_ERROR", message);
    }

    public record StagedContent(
            Path temporaryPath,
            Path targetPath,
            long sizeBytes,
            String sha256) {
    }

    private static ApiException unsafePath() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNSAFE_STORAGE_PATH", "文件存储路径不安全");
    }
}
