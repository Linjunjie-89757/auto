package com.company.autoplatform.execution;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportAttachmentStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "log", "json", "csv", "xml", "yaml", "yml", "png", "jpg", "jpeg", "webp", "pdf", "zip"
    );

    private final Path storageRoot;

    public ReportAttachmentStorageService(@Value("${app.report.storage-root:./data/report-files}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public List<StoredReportFile> storeAll(Long workspaceId, Long reportId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("请先选择要上传的附件");
        }
        files.forEach(this::validateFile);

        List<StoredReportFile> storedFiles = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                storedFiles.add(storeSingle(workspaceId, reportId, file));
            }
            return storedFiles;
        } catch (RuntimeException exception) {
            for (StoredReportFile storedFile : storedFiles) {
                delete(storedFile.storedPath());
            }
            throw exception;
        }
    }

    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        Path target = storageRoot.resolve(storedPath).normalize();
        try {
            Files.deleteIfExists(target);
            deleteEmptyParents(target.getParent());
        } catch (IOException ignored) {
        }
    }

    public ReportFileDownload load(ReportAttachmentEntity attachment) {
        Path target = storageRoot.resolve(attachment.getStoredPath()).normalize();
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists()) {
                throw new BadRequestException("附件文件不存在或已被清理");
            }
            return new ReportFileDownload(
                    resource,
                    attachment.getFileName(),
                    normalizeContentType(attachment.getContentType()),
                    attachment.getFileSize() == null ? 0L : attachment.getFileSize()
            );
        } catch (MalformedURLException exception) {
            throw new BadRequestException("附件下载路径无效");
        }
    }

    private StoredReportFile storeSingle(Long workspaceId, Long reportId, MultipartFile file) {
        String originalName = cleanFileName(file);
        String extension = resolveExtension(originalName);
        String storedName = UUID.randomUUID() + "." + extension;
        Path relativePath = Paths.get("workspace-" + workspaceId, "report-" + reportId, storedName);
        Path target = storageRoot.resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BadRequestException("附件保存失败");
        }
        return new StoredReportFile(relativePath.toString().replace('\\', '/'), normalizeContentType(file.getContentType()), file.getSize());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException("请先选择要上传的附件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("附件大小不能超过 5MB");
        }
        String extension = resolveExtension(cleanFileName(file));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("当前附件类型暂不支持上传");
        }
    }

    private String cleanFileName(MultipartFile file) {
        return StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
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

    private String resolveExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BadRequestException("附件文件名缺少扩展名");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }
}
