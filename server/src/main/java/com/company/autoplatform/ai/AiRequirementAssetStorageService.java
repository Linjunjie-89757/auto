package com.company.autoplatform.ai;

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
public class AiRequirementAssetStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    private final Path storageRoot;

    public AiRequirementAssetStorageService(@Value("${app.ai.asset-storage-root:./data/ai-assets}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public StoredAiRequirementFile storeUploaded(Long userId, MultipartFile file) {
        validateFile(file);
        String originalName = cleanFileName(file.getOriginalFilename() == null ? "asset" : file.getOriginalFilename());
        String extension = resolveExtension(originalName);
        String storedName = UUID.randomUUID() + "." + extension;
        Path relativePath = Paths.get("user-" + userId, storedName);
        Path target = storageRoot.resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BadRequestException("需求图片保存失败");
        }
        return new StoredAiRequirementFile(relativePath.toString().replace('\\', '/'), normalizeContentType(file.getContentType()), file.getSize());
    }

    public StoredAiRequirementFile storeExtracted(Long userId, String fileName, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("文档中的图片内容为空");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new BadRequestException("文档中的图片不能超过 5MB");
        }
        String cleanName = cleanFileName(fileName);
        String extension = resolveExtension(cleanName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("文档中的图片类型暂不支持");
        }
        String storedName = UUID.randomUUID() + "." + extension;
        Path relativePath = Paths.get("user-" + userId, storedName);
        Path target = storageRoot.resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new BadRequestException("文档图片保存失败");
        }
        return new StoredAiRequirementFile(relativePath.toString().replace('\\', '/'), normalizeContentType(contentType), bytes.length);
    }

    public byte[] loadBytes(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BadRequestException("需求图片路径无效");
        }
        Path target = storageRoot.resolve(storedPath).normalize();
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new BadRequestException("需求图片读取失败");
        }
    }

    public Resource loadResource(AiRequirementAssetEntity asset) {
        Path target = storageRoot.resolve(asset.getStoredPath()).normalize();
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists()) {
                throw new BadRequestException("需求图片不存在或已被清理");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new BadRequestException("需求图片路径无效");
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

    public List<StoredAiRequirementFile> storeUploadedAll(Long userId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("请先选择要上传的需求图片");
        }
        List<StoredAiRequirementFile> storedFiles = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                storedFiles.add(storeUploaded(userId, file));
            }
            return storedFiles;
        } catch (RuntimeException exception) {
            storedFiles.forEach(item -> delete(item.storedPath()));
            throw exception;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException("请先选择要上传的需求图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("需求图片大小不能超过 5MB");
        }
        String extension = resolveExtension(cleanFileName(file.getOriginalFilename() == null ? "asset" : file.getOriginalFilename()));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("当前图片类型暂不支持上传");
        }
    }

    private String cleanFileName(String fileName) {
        return StringUtils.cleanPath(fileName);
    }

    private String resolveExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BadRequestException("图片文件名缺少扩展名");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
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

    private String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }
}
