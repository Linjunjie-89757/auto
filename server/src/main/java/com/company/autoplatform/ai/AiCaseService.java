package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.casecenter.CaseDetailResponse;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
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
public class AiCaseService {

    private static final long GLOBAL_WORKSPACE_ID = 0L;
    private static final String GLOBAL_WORKSPACE_CODE = "GLOBAL";
    private static final String GLOBAL_WORKSPACE_NAME = "GLOBAL";
    private static final String ROLE_GENERATOR = "CASE_GENERATOR";
    private static final String ROLE_REVIEWER = "CASE_REVIEWER";

    private final AiCaseConfigMapper aiCaseConfigMapper;
    private final AiRequirementAssetMapper aiRequirementAssetMapper;
    private final WorkspaceService workspaceService;
    private final AiSecretCodec aiSecretCodec;
    private final AiProviderClient aiProviderClient;
    private final AiRequirementAssetStorageService aiRequirementAssetStorageService;

    public AiCaseService(
            AiCaseConfigMapper aiCaseConfigMapper,
            AiRequirementAssetMapper aiRequirementAssetMapper,
            WorkspaceService workspaceService,
            AiSecretCodec aiSecretCodec,
            AiProviderClient aiProviderClient,
            AiRequirementAssetStorageService aiRequirementAssetStorageService
    ) {
        this.aiCaseConfigMapper = aiCaseConfigMapper;
        this.aiRequirementAssetMapper = aiRequirementAssetMapper;
        this.workspaceService = workspaceService;
        this.aiSecretCodec = aiSecretCodec;
        this.aiProviderClient = aiProviderClient;
        this.aiRequirementAssetStorageService = aiRequirementAssetStorageService;
    }

    public AiCaseConfigResponse getConfig(String headerWorkspaceCode, String targetWorkspaceCode) {
        return new AiCaseConfigResponse(
                toItem(findGlobalByRoleType(ROLE_GENERATOR)),
                toItem(findGlobalByRoleType(ROLE_REVIEWER))
        );
    }

    public AiCaseConfigItem createConfig(String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        workspaceService.requirePlatformAdmin();
        String roleType = normalizeRoleType(request.roleType());
        if (findGlobalByRoleType(roleType) != null) {
            throw new BadRequestException("Global AI config already exists for this role");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new BadRequestException("AI API key is required");
        }
        AiCaseConfigEntity entity = new AiCaseConfigEntity();
        entity.setWorkspaceId(GLOBAL_WORKSPACE_ID);
        entity.setRoleType(roleType);
        applyRequest(entity, request, true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiCaseConfigMapper.insert(entity);
        return toItem(entity);
    }

    public AiCaseConfigItem updateConfig(Long id, String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        workspaceService.requirePlatformAdmin();
        AiCaseConfigEntity entity = requireConfig(id);
        String roleType = normalizeRoleType(request.roleType());
        if (!entity.getRoleType().equals(roleType)) {
            throw new BadRequestException("AI config role type cannot be changed");
        }
        applyRequest(entity, request, false);
        entity.setUpdatedAt(LocalDateTime.now());
        aiCaseConfigMapper.updateById(entity);
        return toItem(entity);
    }

    public TestAiCaseConfigResponse testConfig(String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        workspaceService.requirePlatformAdmin();
        String roleType = normalizeRoleType(request.roleType());
        String apiKey = blankToNull(request.apiKey());
        if (apiKey == null) {
            AiCaseConfigEntity existing = findGlobalByRoleType(roleType);
            if (existing != null) {
                apiKey = aiSecretCodec.decrypt(existing.getApiKeyCipherText());
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI API key is required for connection test");
        }

        AiCaseConfigEntity probe = new AiCaseConfigEntity();
        probe.setWorkspaceId(GLOBAL_WORKSPACE_ID);
        probe.setRoleType(roleType);
        applyRequest(probe, request, false);
        probe.setApiKeyCipherText(aiSecretCodec.encrypt(apiKey));

        aiProviderClient.testConnection(probe, apiKey);
        return new TestAiCaseConfigResponse(true, probe.getProvider(), probe.getModel(), "AI connection is available");
    }

    public AiCaseConfigSecretResponse getConfigSecret(Long id, String headerWorkspaceCode) {
        workspaceService.requirePlatformAdmin();
        AiCaseConfigEntity entity = requireConfig(id);
        return new AiCaseConfigSecretResponse(
                entity.getId(),
                entity.getRoleType(),
                aiSecretCodec.decrypt(entity.getApiKeyCipherText())
        );
    }

    public GenerateAiCasesResponse generateCases(String headerWorkspaceCode, GenerateAiCasesRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        AiCaseConfigEntity config = findGlobalByRoleType(ROLE_GENERATOR);
        if (config == null || normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active global case generator config found");
        }
        String decryptedApiKey = aiSecretCodec.decrypt(config.getApiKeyCipherText());
        if (decryptedApiKey == null || decryptedApiKey.isBlank()) {
            throw new BadRequestException("AI config API key is missing");
        }
        int systemMaxCases = config.getMaxCases();
        int requestedMaxCases = request.maxCases() == null ? systemMaxCases : request.maxCases();
        int effectiveMaxCases = Math.min(requestedMaxCases, systemMaxCases);
        List<AiRequirementAssetEntity> assets = loadRequirementAssets(request.assetIds());
        if (!assets.isEmpty() && !supportsImageInput(config)) {
            throw new BadRequestException("鐟滅増鎸告晶鐘绘偨閻旂鐏囨俊顖椻偓宕団偓鐑藉嫉椤忓嫮纾婚柛姘煎灠濞存﹢鎮ч崶顏嗙炕闁稿繈鍎查弫顕€骞愭笟濠勭閻犲洤鍢查崺?AI 闂佹澘绉堕悿鍡樸亜闂堟稒鍎欓柣顫妼閹宕樺鍫㈡Ц");
        }
        String prompt = buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, assets);
        AiGeneratedCasesResult result = aiProviderClient.generate(config, decryptedApiKey, prompt, toImageInputs(assets));
        return new GenerateAiCasesResponse(
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                config.getProvider(),
                config.getModel(),
                systemMaxCases,
                requestedMaxCases,
                effectiveMaxCases,
                result.generatedCases().size(),
                result.generatedCases(),
                result.warnings(),
                result.invalidCases(),
                result.rawContent()
        );
    }

    public ImportRequirementDocumentResponse importRequirementDocument(String headerWorkspaceCode, MultipartFile file) {
        CurrentUserContext.require();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Requirement document is required");
        }
        String fileName = file.getOriginalFilename() == null ? "requirement" : file.getOriginalFilename().trim();
        String extension = resolveExtension(fileName);
        DocumentImportContent imported = switch (extension) {
            case "txt", "md" -> new DocumentImportContent(readPlainText(file), List.of());
            case "docx" -> readDocxContent(file);
            default -> throw new BadRequestException("Only txt, md, and docx requirement documents are supported");
        };
        String normalizedContent = normalizeImportedContent(imported.content());
        if (normalizedContent.isBlank()) {
            throw new BadRequestException("Requirement document content is empty");
        }
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

    public AiReviewResult reviewGeneratedCases(String headerWorkspaceCode, ReviewAiGeneratedCasesRequest request) {
        AiCaseConfigEntity config = requireActiveReviewerConfig();
        String decryptedApiKey = requireConfigApiKey(config);
        String prompt = buildGeneratedCasesReviewPrompt(config, request);
        return aiProviderClient.review(config, decryptedApiKey, prompt);
    }

    public AiReviewResult reviewSavedCase(String headerWorkspaceCode, CaseDetailResponse detail) {
        AiCaseConfigEntity config = requireActiveReviewerConfig();
        String decryptedApiKey = requireConfigApiKey(config);
        String prompt = buildSavedCaseReviewPrompt(config, detail);
        return aiProviderClient.review(config, decryptedApiKey, prompt);
    }

    private AiCaseConfigEntity findByWorkspaceIdAndRoleType(Long workspaceId, String roleType) {
        return aiCaseConfigMapper.selectOne(new LambdaQueryWrapper<AiCaseConfigEntity>()
                .eq(AiCaseConfigEntity::getWorkspaceId, workspaceId)
                .eq(AiCaseConfigEntity::getRoleType, roleType)
                .last("limit 1"));
    }

    private AiCaseConfigEntity findGlobalByRoleType(String roleType) {
        return findByWorkspaceIdAndRoleType(GLOBAL_WORKSPACE_ID, roleType);
    }

    private AiCaseConfigEntity requireConfig(Long id) {
        AiCaseConfigEntity entity = aiCaseConfigMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("AI config does not exist");
        }
        return entity;
    }

    private AiRequirementAssetEntity requireRequirementAsset(Long id) {
        AiRequirementAssetEntity entity = aiRequirementAssetMapper.selectById(id);
        if (entity == null) {
            throw new BadRequestException("Requirement asset does not exist");
        }
        return entity;
    }

    private void applyRequest(AiCaseConfigEntity entity, SaveAiCaseConfigRequest request, boolean creating) {
        entity.setRoleType(normalizeRoleType(request.roleType()));
        entity.setProvider(normalizeProvider(request.provider()));
        entity.setModel(request.model().trim());
        entity.setBaseUrl(request.baseUrl().trim());
        if (creating) {
            entity.setApiKeyCipherText(aiSecretCodec.encrypt(request.apiKey().trim()));
        } else if (request.apiKey() != null) {
            if (request.apiKey().isBlank()) {
                entity.setApiKeyCipherText(null);
            } else {
                entity.setApiKeyCipherText(aiSecretCodec.encrypt(request.apiKey().trim()));
            }
        }
        entity.setPromptTemplate(request.promptTemplate().trim());
        entity.setReviewChecklist(blankToNull(request.reviewChecklist()));
        entity.setTemperature(request.temperature());
        entity.setMaxCases(request.maxCases());
        entity.setSupportsImageInput(Boolean.TRUE.equals(request.supportsImageInput()) ? 1 : 0);
        entity.setStatus(normalizeStatus(request.status()));
    }

    private AiCaseConfigItem toItem(AiCaseConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AiCaseConfigItem(
                entity.getId(),
                GLOBAL_WORKSPACE_CODE,
                GLOBAL_WORKSPACE_NAME,
                entity.getRoleType(),
                entity.getProvider(),
                entity.getModel(),
                entity.getBaseUrl(),
                maskApiKey(aiSecretCodec.decrypt(entity.getApiKeyCipherText())),
                entity.getApiKeyCipherText() != null && !entity.getApiKeyCipherText().isBlank(),
                entity.getPromptTemplate(),
                entity.getReviewChecklist(),
                entity.getTemperature(),
                entity.getMaxCases(),
                supportsImageInput(entity),
                normalizeStatus(entity.getStatus())
        );
    }

    private String buildGeneratorPrompt(
            AiCaseConfigEntity config,
            GenerateAiCasesRequest request,
            WorkspaceEntity workspace,
            int maxCases,
            List<AiRequirementAssetEntity> assets
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getPromptTemplate()).append("\n\n");
        builder.append("[Workspace] ").append(workspace.getWorkspaceName()).append('\n');
        builder.append("[Requirement Title] ").append(request.requirementTitle().trim()).append('\n');
        builder.append("[Requirement Content]\n").append(request.requirementContent().trim()).append("\n\n");
        if (blankToNull(request.sceneFocus()) != null) {
            builder.append("[Focus] ").append(request.sceneFocus().trim()).append('\n');
        }
        if (request.existingCases() != null && !request.existingCases().isEmpty()) {
            builder.append("[Existing Candidate Cases]\n");
            int index = 1;
            for (AiExistingCaseItem item : request.existingCases()) {
                builder.append(index++).append(". ")
                        .append(item.title() == null ? "Untitled case" : item.title().trim())
                        .append(" / ").append(item.priority() == null ? "P1" : item.priority().trim())
                        .append(" / ").append(item.caseType() == null ? "FUNCTION" : item.caseType().trim())
                        .append('\n');
            }
            builder.append("Avoid duplicates with existing candidates. Prefer missing or uncovered scenarios.\n");
        }
        if (blankToNull(request.improvementNotes()) != null) {
            builder.append("[Additional Generation Notes]\n").append(request.improvementNotes().trim()).append("\n");
        }
        if (!assets.isEmpty()) {
            builder.append("[Image Assets] ")
                    .append(assets.size())
                    .append(" requirement images are attached. Use them together with the text requirement.\n");
            int imageIndex = 1;
            for (AiRequirementAssetEntity asset : assets) {
                builder.append("- Image ").append(imageIndex++).append(": ").append(asset.getFileName()).append('\n');
                if (blankToNull(asset.getExtractedText()) != null) {
                    builder.append("  OCR Summary: ").append(asset.getExtractedText().trim()).append('\n');
                }
            }
        }
        builder.append("[Generation Limit] At most ").append(maxCases).append(" cases.\n");
        if (config.getReviewChecklist() != null && !config.getReviewChecklist().isBlank()) {
            builder.append("[Extra Checklist]\n").append(config.getReviewChecklist().trim()).append("\n\n");
        }
        builder.append("""
                [Output Requirements]
                1. Return JSON only. Do not return markdown, explanation, or extra prose.
                2. The response must be either a JSON array or {\"cases\":[...]}.
                3. Every case must contain:
                   - title
                   - caseType
                   - priority
                   - precondition
                   - steps
                   - expectedResult
                   - riskNotes
                4. caseType must be one of: FUNCTION, BOUNDARY, EXCEPTION, REGRESSION.
                5. priority must be one of: P0, P1, P2, P3.
                6. Keep titles, steps, and expected results concrete, executable, and verifiable.
                7. Cover useful test points first. Do not pad with duplicate or low-value cases.
                8. If the requirement only supports fewer valid cases than the limit, return only the reasonable count.
                9. When text and images both provide information, combine them and do not ignore key UI or flow details.
                """);
        return builder.toString();
    }

    private String buildGeneratedCasesReviewPrompt(AiCaseConfigEntity config, ReviewAiGeneratedCasesRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getPromptTemplate()).append("\n\n");
        builder.append("[Requirement Title] ").append(request.requirementTitle().trim()).append('\n');
        builder.append("[Requirement Content]\n").append(request.requirementContent().trim()).append("\n\n");
        if (blankToNull(request.sceneFocus()) != null) {
            builder.append("[Focus] ").append(request.sceneFocus().trim()).append('\n');
        }
        builder.append("[Candidate Cases To Review]\n");
        int index = 1;
        for (AiExistingCaseItem item : request.generatedCases()) {
            builder.append(index++).append(". Title: ").append(nullSafe(item.title())).append('\n');
            builder.append("   Type: ").append(nullSafe(item.caseType()))
                    .append(", Priority: ").append(nullSafe(item.priority())).append('\n');
            builder.append("   Precondition: ").append(nullSafe(item.precondition())).append('\n');
            builder.append("   Steps: ").append(nullSafe(item.steps())).append('\n');
            builder.append("   Expected Result: ").append(nullSafe(item.expectedResult())).append("\n\n");
        }
        if (config.getReviewChecklist() != null && !config.getReviewChecklist().isBlank()) {
            builder.append("[Extra Review Checklist]\n").append(config.getReviewChecklist().trim()).append("\n\n");
        }
        builder.append("""
                [Output Requirements]
                1. Return JSON only. Do not return markdown, explanation, or extra prose.
                2. The response must be:
                   {
                     \"result\":\"APPROVE|REJECT|SUGGEST\",
                     \"summary\":\"one-sentence summary\",
                     \"issues\":[\"issue 1\",\"issue 2\"],
                     \"suggestions\":[\"suggestion 1\",\"suggestion 2\"]
                   }
                3. Use issues to point out missing coverage, duplicates, ambiguity, or non-executable content.
                4. Use suggestions to propose concrete follow-up scenarios or improvements.
                5. Even if the overall quality is good, still provide useful suggestions for strengthening coverage.
                """);
        return builder.toString();
    }

    private String buildSavedCaseReviewPrompt(AiCaseConfigEntity config, CaseDetailResponse detail) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getPromptTemplate()).append("\n\n");
        builder.append("[Case Title] ").append(detail.title()).append('\n');
        builder.append("[Priority] ").append(detail.priority()).append('\n');
        builder.append("[Precondition] ").append(nullSafe(detail.precondition())).append('\n');
        builder.append("[Steps] ").append(nullSafe(detail.steps())).append('\n');
        builder.append("[Expected Result] ").append(nullSafe(detail.expectedResult())).append("\n\n");
        if (config.getReviewChecklist() != null && !config.getReviewChecklist().isBlank()) {
            builder.append("[Extra Review Checklist]\n").append(config.getReviewChecklist().trim()).append("\n\n");
        }
        builder.append("""
                [Output Requirements]
                1. Return JSON only. Do not return markdown, explanation, or extra prose.
                2. The response must be:
                   {
                     \"result\":\"APPROVE|REJECT|SUGGEST\",
                     \"summary\":\"one-sentence summary\",
                     \"issues\":[\"issue 1\",\"issue 2\"],
                     \"suggestions\":[\"suggestion 1\",\"suggestion 2\"]
                   }
                3. Focus on whether the case is clear, complete, executable, and verifiable.
                """);
        return builder.toString();
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeRoleType(String roleType) {
        String normalized = roleType == null ? "" : roleType.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_GENERATOR.equals(normalized) && !ROLE_REVIEWER.equals(normalized)) {
            throw new BadRequestException("AI role type must be CASE_GENERATOR or CASE_REVIEWER");
        }
        return normalized;
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null) {
            return 1;
        }
        if (status != 0 && status != 1) {
            throw new BadRequestException("AI config status must be 0 or 1");
        }
        return status;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private AiCaseConfigEntity requireActiveReviewerConfig() {
        AiCaseConfigEntity config = findGlobalByRoleType(ROLE_REVIEWER);
        if (config == null || normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active global case reviewer config found");
        }
        return config;
    }

    private String requireConfigApiKey(AiCaseConfigEntity config) {
        String decryptedApiKey = aiSecretCodec.decrypt(config.getApiKeyCipherText());
        if (decryptedApiKey == null || decryptedApiKey.isBlank()) {
            throw new BadRequestException("AI config API key is missing");
        }
        return decryptedApiKey;
    }

    private String nullSafe(String value) {
        return blankToNull(value) == null ? "-" : value.trim();
    }

    private String resolveExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).trim().toLowerCase(Locale.ROOT);
    }

    private String readPlainText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BadRequestException("Failed to read requirement document");
        }
    }

    private DocumentImportContent readDocxContent(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); XWPFDocument document = new XWPFDocument(inputStream)) {
            String content = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
                    .collect(Collectors.joining("\n"));
            List<ExtractedRequirementImage> images = document.getAllPictures().stream()
                    .map(this::toExtractedImage)
                    .toList();
            return new DocumentImportContent(content, images);
        } catch (IOException exception) {
            throw new BadRequestException("Failed to parse docx requirement document");
        }
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

    private List<AiRequirementAssetEntity> loadRequirementAssets(List<Long> assetIds) {
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

    private List<AiProviderClient.ImageInput> toImageInputs(List<AiRequirementAssetEntity> assets) {
        return assets.stream()
                .map(asset -> new AiProviderClient.ImageInput(
                        asset.getFileName(),
                        asset.getContentType() == null ? "image/png" : asset.getContentType(),
                        aiRequirementAssetStorageService.loadBytes(asset.getStoredPath())
                ))
                .toList();
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

    private boolean supportsImageInput(AiCaseConfigEntity entity) {
        return entity.getSupportsImageInput() != null && entity.getSupportsImageInput() == 1;
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

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "*".repeat(apiKey.length());
        }
        return apiKey.substring(0, 4) + "*".repeat(apiKey.length() - 8) + apiKey.substring(apiKey.length() - 4);
    }
}



