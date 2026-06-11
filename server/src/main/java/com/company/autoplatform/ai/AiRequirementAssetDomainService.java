package com.company.autoplatform.ai;

import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AiRequirementAssetDomainService {

    private final AiRequirementAssetMapper aiRequirementAssetMapper;
    private final AiRequirementAssetStorageService aiRequirementAssetStorageService;

    public AiRequirementAssetDomainService(
            AiRequirementAssetMapper aiRequirementAssetMapper,
            AiRequirementAssetStorageService aiRequirementAssetStorageService
    ) {
        this.aiRequirementAssetMapper = aiRequirementAssetMapper;
        this.aiRequirementAssetStorageService = aiRequirementAssetStorageService;
    }

    public ImportRequirementDocumentResponse importRequirementDocument(String headerWorkspaceCode, MultipartFile file) {
        CurrentUserContext.require();
        validateSelectedFile(file, "Please select a requirement document first");
        String fileName = file.getOriginalFilename() == null ? "requirement" : file.getOriginalFilename().trim();
        if (file.getSize() <= 0) {
            return new ImportRequirementDocumentResponse(
                    fileName,
                    resolveImportedTitle(fileName, ""),
                    "",
                    0,
                    List.of()
            );
        }
        String extension = resolveExtension(fileName);
        DocumentImportContent imported = switch (extension) {
            case "txt", "md" -> new DocumentImportContent(readPlainText(file), List.of());
            case "docx" -> readDocxContent(file);
            default -> throw new BadRequestException("Only txt, md, and docx requirement documents are supported");
        };
        String normalizedContent = normalizeImportedContent(imported.content());
        List<AiRequirementAssetResponse> assets = imported.images().stream()
                .map(image -> createExtractedAsset(CurrentUserContext.get(), image))
                .toList();
        return new ImportRequirementDocumentResponse(
                fileName,
                resolveImportedTitle(fileName, normalizedContent),
                normalizedContent,
                normalizedContent.length(),
                assets
        );
    }

    public List<AiRequirementAssetResponse> uploadRequirementAssets(String headerWorkspaceCode, List<MultipartFile> files) {
        Long userId = CurrentUserContext.get();
        List<StoredAiRequirementFile> storedFiles = aiRequirementAssetStorageService.storeUploadedAll(userId, files);
        List<AiRequirementAssetEntity> createdAssets = new ArrayList<>();
        try {
            for (int i = 0; i < storedFiles.size(); i++) {
                MultipartFile file = files.get(i);
                StoredAiRequirementFile stored = storedFiles.get(i);
                AiRequirementAssetEntity entity = new AiRequirementAssetEntity();
                entity.setUserId(userId);
                entity.setSourceType("MANUAL_UPLOAD");
                entity.setFileName(file.getOriginalFilename());
                entity.setStoredPath(stored.storedPath());
                entity.setContentType(stored.contentType());
                entity.setFileSize(stored.fileSize());
                entity.setExtractedText(null);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                aiRequirementAssetMapper.insert(entity);
                createdAssets.add(entity);
            }
        } catch (RuntimeException exception) {
            createdAssets.forEach(item -> {
                if (item.getId() != null) {
                    aiRequirementAssetMapper.deleteById(item.getId());
                }
                aiRequirementAssetStorageService.delete(item.getStoredPath());
            });
            storedFiles.forEach(item -> aiRequirementAssetStorageService.delete(item.storedPath()));
            throw exception;
        }
        return createdAssets.stream().map(this::toAssetResponse).toList();
    }

    public void deleteRequirementAsset(Long id, String headerWorkspaceCode) {
        AiRequirementAssetEntity asset = requireRequirementAsset(id);
        validateAssetOwner(asset);
        aiRequirementAssetStorageService.delete(asset.getStoredPath());
        aiRequirementAssetMapper.deleteById(id);
    }

    public AiRequirementAssetDownload downloadRequirementAsset(Long id, String headerWorkspaceCode) {
        AiRequirementAssetEntity asset = requireRequirementAsset(id);
        validateAssetOwner(asset);
        Resource resource = aiRequirementAssetStorageService.loadResource(asset);
        return new AiRequirementAssetDownload(
                resource,
                asset.getFileName(),
                asset.getContentType() == null ? "application/octet-stream" : asset.getContentType(),
                asset.getFileSize() == null ? 0L : asset.getFileSize()
        );
    }

    AiRequirementAssetEntity requireRequirementAsset(Long id) {
        AiRequirementAssetEntity entity = aiRequirementAssetMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("Requirement asset does not exist");
        }
        return entity;
    }

    List<AiRequirementAssetEntity> loadRequirementAssets(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        Long userId = CurrentUserContext.get();
        return assetIds.stream().map(this::requireRequirementAsset).peek(asset -> {
            if (!asset.getUserId().equals(userId)) {
                throw new BadRequestException("You do not have permission to use this requirement asset");
            }
        }).toList();
    }

    List<AiProviderClient.ImageInput> toImageInputs(List<AiRequirementAssetEntity> assets) {
        return assets.stream()
                .map(asset -> new AiProviderClient.ImageInput(
                        asset.getFileName(),
                        asset.getContentType() == null ? "image/png" : asset.getContentType(),
                        aiRequirementAssetStorageService.loadBytes(asset.getStoredPath())
                ))
                .toList();
    }

    private String readPlainText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BadRequestException("Failed to read requirement document");
        }
    }

    private DocumentImportContent readDocxContent(MultipartFile file) {
        if (file.getSize() <= 0) {
            return new DocumentImportContent("", List.of());
        }
        try (InputStream inputStream = file.getInputStream(); XWPFDocument document = new XWPFDocument(inputStream)) {
            String content = document.getBodyElements().stream()
                    .map(this::extractBodyElementText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
            List<ExtractedRequirementImage> images = document.getAllPictures().stream()
                    .map(this::toExtractedImage)
                    .toList();
            return new DocumentImportContent(content, images);
        } catch (IOException exception) {
            throw new BadRequestException("Failed to parse docx requirement document");
        }
    }

    private String extractBodyElementText(IBodyElement element) {
        if (element == null) {
            return "";
        }
        if (element.getElementType() == BodyElementType.PARAGRAPH) {
            return extractParagraphText((XWPFParagraph) element);
        }
        if (element.getElementType() == BodyElementType.TABLE) {
            return extractTableText((XWPFTable) element);
        }
        return "";
    }

    private String extractParagraphText(XWPFParagraph paragraph) {
        String text = normalizeImportedLine(paragraph == null ? "" : paragraph.getText());
        if (text.isBlank()) {
            return "";
        }
        String styleId = paragraph.getStyle();
        if (styleId != null) {
            String normalizedStyle = styleId.trim().toLowerCase(Locale.ROOT);
            if (normalizedStyle.startsWith("heading")) {
                String level = normalizedStyle.replaceAll("[^0-9]", "");
                int headingLevel = level.isBlank() ? 1 : Math.max(1, Math.min(6, Integer.parseInt(level)));
                return "#".repeat(headingLevel) + " " + text;
            }
        }
        if (paragraph.getNumID() != null) {
            return "- " + text;
        }
        return text;
    }

    private String extractTableText(XWPFTable table) {
        if (table == null || table.getRows() == null || table.getRows().isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = rows.get(rowIndex);
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            List<String> values = cells.stream()
                    .map(XWPFTableCell::getText)
                    .map(this::normalizeImportedLine)
                    .toList();
            lines.add("| " + String.join(" | ", values) + " |");
            if (rowIndex == 0) {
                lines.add("| " + values.stream().map(value -> "---").collect(Collectors.joining(" | ")) + " |");
            }
        }
        return String.join("\n", lines);
    }

    private String normalizeImportedLine(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeImportedContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').trim();
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized;
    }

    private String resolveImportedTitle(String fileName, String content) {
        String headingCandidate = null;
        for (String line : content.split("\n")) {
            String candidate = line.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith("#")) {
                candidate = candidate.replaceFirst("^#+\\s*", "").trim();
                if (!candidate.isEmpty()) {
                    headingCandidate = normalizeImportedTitleLine(candidate);
                    break;
                }
            }
        }
        if (headingCandidate != null && !headingCandidate.isEmpty()) {
            return trimTitleLength(headingCandidate);
        }

        for (String line : content.split("\n")) {
            String candidate = normalizeImportedTitleLine(line);
            if (!candidate.isEmpty()) {
                return trimTitleLength(candidate);
            }
        }
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String normalizeImportedTitleLine(String line) {
        if (line == null) {
            return "";
        }
        String candidate = line.trim();
        candidate = candidate.replaceFirst("^#+\\s*", "");
        candidate = candidate.replaceFirst("^[0-9]+[.)]\\s*", "");
        candidate = candidate.replaceFirst("^[-*]\\s*", "");
        candidate = candidate.replaceFirst("^(requirement title|title|subject)[:\\s]*", "");
        candidate = candidate.trim();
        if (candidate.length() < 2) {
            return "";
        }
        if (candidate.length() > 60 && candidate.contains(" ")) {
            return "";
        }
        return candidate;
    }

    private String trimTitleLength(String title) {
        return title.length() > 80 ? title.substring(0, 80) : title;
    }

    private void validateSelectedFile(MultipartFile file, String missingMessage) {
        if (file == null || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BadRequestException(missingMessage);
        }
    }

    private String resolveExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).trim().toLowerCase(Locale.ROOT);
    }

    private ExtractedRequirementImage toExtractedImage(XWPFPictureData pictureData) {
        String fileName = pictureData.getFileName() == null ? "requirement-image.png" : pictureData.getFileName();
        String contentType = pictureData.getPackagePart().getContentType();
        String extractedText = null;
        return new ExtractedRequirementImage(fileName, pictureData.getData(), contentType, extractedText);
    }

    private AiRequirementAssetResponse createExtractedAsset(Long userId, ExtractedRequirementImage image) {
        StoredAiRequirementFile stored = aiRequirementAssetStorageService.storeExtracted(
                userId,
                image.fileName(),
                image.bytes(),
                image.contentType()
        );
        AiRequirementAssetEntity entity = new AiRequirementAssetEntity();
        entity.setUserId(userId);
        entity.setSourceType("DOCX_EXTRACTED");
        entity.setFileName(image.fileName());
        entity.setStoredPath(stored.storedPath());
        entity.setContentType(stored.contentType());
        entity.setFileSize(stored.fileSize());
        entity.setExtractedText(image.extractedText());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiRequirementAssetMapper.insert(entity);
        return toAssetResponse(entity);
    }

    private AiRequirementAssetResponse toAssetResponse(AiRequirementAssetEntity entity) {
        return new AiRequirementAssetResponse(
                entity.getId(),
                entity.getSourceType(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getExtractedText(),
                "/api/cases/ai/assets/" + entity.getId() + "/download",
                entity.getCreatedAt()
        );
    }

    private void validateAssetOwner(AiRequirementAssetEntity asset) {
        if (!asset.getUserId().equals(CurrentUserContext.get())) {
            throw new BadRequestException("You do not have permission to modify this requirement asset");
        }
    }

    private record DocumentImportContent(
            String content,
            List<ExtractedRequirementImage> images
    ) {
    }

    private record ExtractedRequirementImage(
            String fileName,
            byte[] bytes,
            String contentType,
            String extractedText
    ) {
    }
}
