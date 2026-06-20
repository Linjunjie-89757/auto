package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class WebUiArtifactStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final String CONTENT_TYPE = "image/png";

    private final Path storageRoot;

    public WebUiArtifactStorageService(@Value("${app.web-ui.storage-root:./data/web-ui-artifacts}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    StoredArtifact storeScreenshot(Long workspaceId, Long runId, Long stepId, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Screenshot content cannot be empty");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new BadRequestException("Screenshot size cannot exceed 5MB");
        }
        String fileName = "screenshot-" + UUID.randomUUID() + ".png";
        Path relativePath = Paths.get("workspace-" + workspaceId, "run-" + runId, fileName);
        Path target = storageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BadRequestException("Screenshot storage path is invalid");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new BadRequestException("Screenshot save failed");
        }
        return new StoredArtifact(
                fileName,
                CONTENT_TYPE,
                (long) bytes.length,
                relativePath.toString().replace('\\', '/')
        );
    }

    WebUiArtifactFileDownload load(WebUiRunArtifactEntity artifact) {
        Path target = storageRoot.resolve(artifact.getStoragePath()).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BadRequestException("Screenshot download path is invalid");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists()) {
                throw new BadRequestException("Screenshot file does not exist or has been cleaned up");
            }
            return new WebUiArtifactFileDownload(
                    resource,
                    artifact.getFileName(),
                    artifact.getContentType() == null ? CONTENT_TYPE : artifact.getContentType(),
                    artifact.getFileSize() == null ? 0L : artifact.getFileSize()
            );
        } catch (MalformedURLException exception) {
            throw new BadRequestException("Screenshot download path is invalid");
        }
    }

    void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        Path target = storageRoot.resolve(storagePath).normalize();
        try {
            Files.deleteIfExists(target);
            deleteEmptyParents(target.getParent());
        } catch (IOException ignored) {
        }
    }

    private void deleteEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && current.startsWith(storageRoot) && !current.equals(storageRoot)) {
            if (!Files.exists(current)) {
                current = current.getParent();
                continue;
            }
            try (var stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    record StoredArtifact(
            String fileName,
            String contentType,
            long fileSize,
            String storagePath
    ) {
    }
}
