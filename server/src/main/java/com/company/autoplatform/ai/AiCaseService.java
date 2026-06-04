package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.casecenter.CaseDetailResponse;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AiCaseService {

    private static final long PERSONAL_SCOPE_WORKSPACE_ID = 0L;
    private static final String PERSONAL_SCOPE_WORKSPACE_CODE = "PERSONAL";
    private static final String PERSONAL_SCOPE_WORKSPACE_NAME = "我的配置";
    private static final String ROLE_GENERATOR = "CASE_GENERATOR";
    private static final String ROLE_REVIEWER = "CASE_REVIEWER";
    private static final String PROTOCOL_OPENAI_CHAT = AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT;
    private static final String PROTOCOL_OPENAI_RESPONSES = AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_RESPONSES;
    private static final String PROTOCOL_AZURE_OPENAI = "AZURE_OPENAI";
    public static final int INITIAL_SMART_MAX_CASES = 50;
    public static final int REVIEW_SUPPLEMENT_MAX_CASES = 30;
    public static final int FINAL_MAX_CASES = 80;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiCaseConfigMapper aiCaseConfigMapper;
    private final AiProviderConnectionMapper aiProviderConnectionMapper;
    private final AiProviderModelMapper aiProviderModelMapper;
    private final AiRequirementAssetMapper aiRequirementAssetMapper;
    private final WorkspaceService workspaceService;
    private final AiSecretCodec aiSecretCodec;
    private final AiProviderClient aiProviderClient;
    private final AiRequirementAssetStorageService aiRequirementAssetStorageService;
    private final int defaultRequestTimeoutSeconds;

    public AiCaseService(
            AiCaseConfigMapper aiCaseConfigMapper,
            AiProviderConnectionMapper aiProviderConnectionMapper,
            AiProviderModelMapper aiProviderModelMapper,
            AiRequirementAssetMapper aiRequirementAssetMapper,
            WorkspaceService workspaceService,
            AiSecretCodec aiSecretCodec,
            AiProviderClient aiProviderClient,
            AiRequirementAssetStorageService aiRequirementAssetStorageService,
            @Value("${app.ai.request-timeout-seconds:60}") int defaultRequestTimeoutSeconds
    ) {
        this.aiCaseConfigMapper = aiCaseConfigMapper;
        this.aiProviderConnectionMapper = aiProviderConnectionMapper;
        this.aiProviderModelMapper = aiProviderModelMapper;
        this.aiRequirementAssetMapper = aiRequirementAssetMapper;
        this.workspaceService = workspaceService;
        this.aiSecretCodec = aiSecretCodec;
        this.aiProviderClient = aiProviderClient;
        this.aiRequirementAssetStorageService = aiRequirementAssetStorageService;
        this.defaultRequestTimeoutSeconds = Math.max(10, Math.min(600, defaultRequestTimeoutSeconds));
    }

    public AiCaseConfigResponse getConfig(String headerWorkspaceCode, String targetWorkspaceCode) {
        Long ownerUserId = CurrentUserContext.get();
        AiCaseConfigItem generatorConfig = toItem(findByOwnerUserIdAndRoleType(ownerUserId, ROLE_GENERATOR));
        AiCaseConfigItem reviewerConfig = toItem(findByOwnerUserIdAndRoleType(ownerUserId, ROLE_REVIEWER));
        boolean hasLegacyConfig = hasLegacyConfig();
        return new AiCaseConfigResponse(
                generatorConfig,
                reviewerConfig,
                hasLegacyConfig,
                hasLegacyConfig && generatorConfig == null && reviewerConfig == null
        );
    }

    public AiCaseConfigItem createConfig(String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        Long ownerUserId = CurrentUserContext.get();
        String roleType = normalizeRoleType(request.roleType());
        if (findByOwnerUserIdAndRoleType(ownerUserId, roleType) != null) {
            throw new BadRequestException("AI config already exists for this role");
        }
        AiCaseConfigEntity entity = new AiCaseConfigEntity();
        entity.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        entity.setOwnerUserId(ownerUserId);
        entity.setRoleType(roleType);
        applyRoleRequest(entity, request, null, true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiCaseConfigMapper.insert(entity);
        return toItem(entity);
    }

    public AiCaseConfigItem updateConfig(Long id, String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        AiCaseConfigEntity entity = requireConfig(id);
        String roleType = normalizeRoleType(request.roleType());
        if (!entity.getRoleType().equals(roleType)) {
            throw new BadRequestException("AI config role type cannot be changed");
        }
        applyRoleRequest(entity, request, entity, false);
        entity.setUpdatedAt(LocalDateTime.now());
        aiCaseConfigMapper.updateById(entity);
        return toItem(entity);
    }

    public TestAiCaseConfigResponse testConfig(String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        Long ownerUserId = CurrentUserContext.get();
        String roleType = normalizeRoleType(request.roleType());
        if (request.providerConnectionId() != null) {
            AiProviderConnectionEntity connection = requireProviderConnection(request.providerConnectionId());
            String apiKey = requireProviderApiKey(connection);
            AiProviderRequestProfile profile = buildProviderProfile(
                    connection,
                    request.model().trim(),
                    request.temperature(),
                    request.maxCases()
            );
            aiProviderClient.testConnection(profile, apiKey);
            return new TestAiCaseConfigResponse(true, profile.provider(), profile.model(), "AI connection is available");
        }
        String apiKey = blankToNull(request.apiKey());
        if (apiKey == null) {
            AiCaseConfigEntity existing = findByOwnerUserIdAndRoleType(ownerUserId, roleType);
            if (existing != null) {
                AiProviderConnectionEntity connection = resolveBoundConnection(existing);
                apiKey = connection != null
                        ? aiSecretCodec.decrypt(connection.getApiKeyCipherText())
                        : aiSecretCodec.decrypt(existing.getApiKeyCipherText());
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI API key is required for connection test");
        }
        AiProviderRequestProfile profile = buildLegacyProfile(roleType, request, findByOwnerUserIdAndRoleType(ownerUserId, roleType));
        aiProviderClient.testConnection(profile, apiKey);
        return new TestAiCaseConfigResponse(true, profile.provider(), profile.model(), "AI connection is available");
    }

    public AiCaseConfigSecretResponse getConfigSecret(Long id, String headerWorkspaceCode) {
        AiCaseConfigEntity entity = requireConfig(id);
        AiProviderConnectionEntity connection = resolveBoundConnection(entity);
        String apiKey = connection != null
                ? aiSecretCodec.decrypt(connection.getApiKeyCipherText())
                : aiSecretCodec.decrypt(entity.getApiKeyCipherText());
        return new AiCaseConfigSecretResponse(
                entity.getId(),
                entity.getRoleType(),
                apiKey
        );
    }

    public AiProviderConnectionSecretResponse getProviderSecret(Long id, String headerWorkspaceCode) {
        AiProviderConnectionEntity entity = requireProviderConnection(id);
        return new AiProviderConnectionSecretResponse(
                entity.getId(),
                requireProviderApiKey(entity)
        );
    }

    public void validateGenerationImageSupport(List<Long> assetIds) {
        List<AiRequirementAssetEntity> assets = loadRequirementAssets(assetIds);
        if (assets.isEmpty()) {
            return;
        }
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_GENERATOR);
        if (!supportsImageInputForGeneration(resolved)) {
            throw new BadRequestException("当前生成模型不支持图片识别，是否忽略图片并仅基于文本继续生成？");
        }
    }

    public GenerateAiCasesResponse generateCases(String headerWorkspaceCode, GenerateAiCasesRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_GENERATOR);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active personal case generator config found");
        }
        int systemMaxCases = INITIAL_SMART_MAX_CASES;
        int requestedMaxCases = request.maxCases() == null ? INITIAL_SMART_MAX_CASES : request.maxCases();
        int effectiveMaxCases = Math.min(requestedMaxCases, INITIAL_SMART_MAX_CASES);
        List<AiRequirementAssetEntity> assets = loadRequirementAssets(request.assetIds());
        if (!assets.isEmpty() && !supportsImageInputForGeneration(resolved)) {
            throw new BadRequestException("The current AI config does not support image input. Remove the images or enable an image-capable model.");
        }
        boolean ignoredImages = false;
        String prompt = buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, assets, false);
        AiGeneratedCasesResult result;
        try {
            result = aiProviderClient.generate(
                    resolved.profileWithMaxCases(effectiveMaxCases),
                    resolved.apiKey(),
                    prompt,
                    toImageInputs(assets)
            );
        } catch (BadRequestException exception) {
            if (assets.isEmpty() || !isImageInputUnsupportedError(exception)) {
                throw exception;
            }
            ignoredImages = true;
            prompt = buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, List.of(), false);
            result = aiProviderClient.generate(
                    resolved.profileWithMaxCases(effectiveMaxCases),
                    resolved.apiKey(),
                    prompt,
                    List.of()
            );
        }
        return new GenerateAiCasesResponse(
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                resolved.profile().provider(),
                config.getModel(),
                systemMaxCases,
                requestedMaxCases,
                effectiveMaxCases,
                result.generatedCases().size(),
                result.generatedCases(),
                result.coverageSummary(),
                result.remainingCoverageGaps(),
                result.warnings(),
                result.invalidCases(),
                result.rawContent(),
                ignoredImages
        );
    }

    public StreamedGenerateCasesResult streamGenerateCases(
            String headerWorkspaceCode,
            GenerateAiCasesRequest request,
            Consumer<AiStreamModelInfo> modelConsumer,
            Consumer<GeneratedCaseStreamUpdate> caseConsumer
    ) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_GENERATOR);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active personal case generator config found");
        }
        int systemMaxCases = INITIAL_SMART_MAX_CASES;
        int requestedMaxCases = request.maxCases() == null ? INITIAL_SMART_MAX_CASES : request.maxCases();
        int effectiveMaxCases = Math.min(requestedMaxCases, INITIAL_SMART_MAX_CASES);
        List<AiRequirementAssetEntity> assets = loadRequirementAssets(request.assetIds());
        if (!assets.isEmpty() && !supportsImageInputForGeneration(resolved)) {
            throw new BadRequestException("The current AI config does not support image input. Remove the images or enable an image-capable model.");
        }
        if (modelConsumer != null) {
            modelConsumer.accept(new AiStreamModelInfo(resolved.profile().provider(), config.getModel()));
        }
        boolean ignoredImages = false;
        String prompt = buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, assets, true);
        List<GeneratedAiCaseItem> generatedCases = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<AiInvalidCaseItem> invalidCases = new ArrayList<>();
        StringBuilder rawOutput = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();
        Consumer<String> deltaConsumer = delta -> {
            rawOutput.append(delta);
            lineBuffer.append(delta);
            drainCompleteLines(lineBuffer, line -> emitGeneratedCaseLine(
                    line,
                    effectiveMaxCases,
                    generatedCases,
                    warnings,
                    invalidCases,
                    rawOutput,
                    caseConsumer
            ));
        };

        AiProviderClient.StreamContentResult streamResult;
        try {
            streamResult = aiProviderClient.streamStructuredContentWithResult(
                    resolved.profileWithMaxCases(effectiveMaxCases),
                    resolved.apiKey(),
                    prompt,
                    deltaConsumer
            );
        } catch (BadRequestException exception) {
            if (assets.isEmpty() || !isImageInputUnsupportedError(exception)) {
                throw exception;
            }
            ignoredImages = true;
            rawOutput.setLength(0);
            lineBuffer.setLength(0);
            generatedCases.clear();
            warnings.clear();
            invalidCases.clear();
            prompt = buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, List.of(), true);
            streamResult = aiProviderClient.streamStructuredContentWithResult(
                    resolved.profileWithMaxCases(effectiveMaxCases),
                    resolved.apiKey(),
                    prompt,
                    deltaConsumer
            );
        }
        String finalContent = streamResult.content();
        String rawContent = finalContent == null || finalContent.isBlank() ? rawOutput.toString() : finalContent;
        emitGeneratedCaseLine(
                lineBuffer.toString(),
                effectiveMaxCases,
                generatedCases,
                warnings,
                invalidCases,
                new StringBuilder(rawContent),
                caseConsumer
        );
        if (generatedCases.isEmpty()) {
            AiGeneratedCasesResult parsed = aiProviderClient.parseGeneratedCasesContent(rawContent, effectiveMaxCases);
            warnings.addAll(parsed.warnings());
            invalidCases.addAll(parsed.invalidCases());
            for (GeneratedAiCaseItem item : parsed.generatedCases()) {
                if (generatedCases.size() >= effectiveMaxCases) {
                    break;
                }
                generatedCases.add(item);
                if (caseConsumer != null) {
                    caseConsumer.accept(new GeneratedCaseStreamUpdate(
                            generatedCases.size() - 1,
                            item,
                            rawContent
                    ));
                }
            }
        }
        return new StreamedGenerateCasesResult(
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                resolved.profile().provider(),
                config.getModel(),
                systemMaxCases,
                requestedMaxCases,
                effectiveMaxCases,
                generatedCases.size(),
                generatedCases,
                blankToNull(generationCoverageSummary(rawContent)),
                generationRemainingCoverageGaps(rawContent),
                warnings,
                invalidCases,
                rawContent,
                streamResult.fallbackToComplete(),
                streamResult.fallbackReason(),
                ignoredImages
        );
    }

    public List<AiProviderConnectionItem> getProviders(String headerWorkspaceCode) {
        Long ownerUserId = CurrentUserContext.get();
        return aiProviderConnectionMapper.selectList(new LambdaQueryWrapper<AiProviderConnectionEntity>()
                        .eq(AiProviderConnectionEntity::getOwnerUserId, ownerUserId)
                        .orderByDesc(AiProviderConnectionEntity::getUpdatedAt))
                .stream()
                .map(this::toConnectionItem)
                .toList();
    }

    public AiProviderConnectionItem createProvider(String headerWorkspaceCode, SaveAiProviderConnectionRequest request) {
        if (blankToNull(request.apiKey()) == null) {
            throw new BadRequestException("AI API Key 不能为空");
        }
        AiProviderConnectionEntity entity = new AiProviderConnectionEntity();
        entity.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        entity.setOwnerUserId(CurrentUserContext.get());
        applyProviderRequest(entity, request, true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiProviderConnectionMapper.insert(entity);
        syncRequestedModel(entity, request.modelName());
        return toConnectionItem(entity);
    }

    public AiProviderConnectionItem updateProvider(Long id, String headerWorkspaceCode, SaveAiProviderConnectionRequest request) {
        AiProviderConnectionEntity entity = requireProviderConnection(id);
        applyProviderRequest(entity, request, false);
        entity.setUpdatedAt(LocalDateTime.now());
        aiProviderConnectionMapper.updateById(entity);
        syncRequestedModel(entity, request.modelName());
        return toConnectionItem(entity);
    }

    public PreviewAiProviderModelsResponse previewProviderModels(String headerWorkspaceCode, PreviewAiProviderModelsRequest request) {
        String apiKey = blankToNull(request.apiKey());
        if (apiKey == null) {
            throw new BadRequestException("AI API key is required");
        }
        AiModelFetchResult fetchResult = aiProviderClient.fetchModels(
                buildPreviewProviderProfile(request),
                apiKey
        );
        return new PreviewAiProviderModelsResponse(
                fetchResult.models(),
                LocalDateTime.now(),
                fetchResult.message()
        );
    }

    @Transactional
    public void deleteProvider(Long id, String headerWorkspaceCode) {
        requireProviderConnection(id);
        aiCaseConfigMapper.update(null, new LambdaUpdateWrapper<AiCaseConfigEntity>()
                .eq(AiCaseConfigEntity::getOwnerUserId, CurrentUserContext.get())
                .eq(AiCaseConfigEntity::getProviderConnectionId, id)
                .set(AiCaseConfigEntity::getProviderConnectionId, null)
                .set(AiCaseConfigEntity::getApiKeyCipherText, null)
                .set(AiCaseConfigEntity::getCapabilityOverrideJson, null)
                .set(AiCaseConfigEntity::getSupportsImageInput, 0)
                .set(AiCaseConfigEntity::getStatus, 0)
                .set(AiCaseConfigEntity::getUpdatedAt, LocalDateTime.now()));
        aiProviderModelMapper.delete(new LambdaQueryWrapper<AiProviderModelEntity>()
                .eq(AiProviderModelEntity::getConnectionId, id));
        aiProviderConnectionMapper.deleteById(id);
    }

    public TestAiProviderConnectionResponse testProvider(Long id, String headerWorkspaceCode) {
        AiProviderConnectionEntity entity = requireProviderConnection(id);
        String apiKey = requireProviderApiKey(entity);
        String modelName = resolvePreferredModelForConnection(entity.getId());
        if (blankToNull(modelName) == null) {
            AiModelFetchResult fetched = aiProviderClient.fetchModels(
                    buildProviderProfile(entity, "model-probe", 0.3, null),
                    apiKey
            );
            if (!fetched.models().isEmpty()) {
                persistFetchedModels(entity, fetched.models(), LocalDateTime.now());
                modelName = fetched.models().get(0).modelName();
            }
        }
        if (blankToNull(modelName) == null) {
            throw new BadRequestException("当前连接还没有可用模型，请先获取模型列表或在角色绑定里手工指定模型");
        }
        AiProviderRequestProfile profile = buildProviderProfile(entity, modelName, 0.3, null);
        aiProviderClient.testConnection(profile, apiKey);
        entity.setLastVerifiedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiProviderConnectionMapper.updateById(entity);
        return new TestAiProviderConnectionResponse(
                true,
                entity.getId(),
                entity.getConnectionName(),
                entity.getProtocolType(),
                "连接测试成功",
                entity.getLastVerifiedAt()
        );
    }

    public FetchAiProviderModelsResponse fetchProviderModels(Long id, String headerWorkspaceCode) {
        AiProviderConnectionEntity entity = requireProviderConnection(id);
        String apiKey = requireProviderApiKey(entity);
        AiModelFetchResult fetchResult = aiProviderClient.fetchModels(
                buildProviderProfile(entity, "gpt-4o-mini", 0.3, null),
                apiKey
        );
        LocalDateTime now = LocalDateTime.now();
        List<AiProviderModelItem> updatedModels = persistFetchedModels(entity, fetchResult.models(), now);
        entity.setLastFetchModelsAt(now);
        entity.setUpdatedAt(now);
        aiProviderConnectionMapper.updateById(entity);
        return new FetchAiProviderModelsResponse(
                entity.getId(),
                entity.getConnectionName(),
                updatedModels,
                now,
                fetchResult.message()
        );
    }

    public List<AiProviderModelItem> getProviderModels(Long id, String headerWorkspaceCode) {
        requireProviderConnection(id);
        return listModelItems(id);
    }

    public AiProviderModelItem probeProviderModel(Long id, String headerWorkspaceCode, ProbeAiProviderModelRequest request) {
        AiProviderConnectionEntity entity = requireProviderConnection(id);
        String apiKey = requireProviderApiKey(entity);
        AiProviderRequestProfile profile = buildProviderProfile(entity, request.modelName().trim(), 0.3, null);
        AiModelCapabilities capabilities = aiProviderClient.probeCapabilities(profile, apiKey);
        LocalDateTime now = LocalDateTime.now();
        AiProviderModelEntity modelEntity = findProviderModelByName(id, request.modelName().trim());
        if (modelEntity == null) {
            modelEntity = new AiProviderModelEntity();
            modelEntity.setConnectionId(id);
            modelEntity.setModelName(request.modelName().trim());
            modelEntity.setDisplayName(request.modelName().trim());
            modelEntity.setSelectable(1);
            modelEntity.setCreatedAt(now);
        }
        modelEntity.setDetectedCapabilitiesJson(writeCapabilities(capabilities));
        modelEntity.setLastProbedAt(now);
        modelEntity.setUpdatedAt(now);
        if (modelEntity.getId() == null) {
            aiProviderModelMapper.insert(modelEntity);
        } else {
            aiProviderModelMapper.updateById(modelEntity);
        }
        return toModelItem(modelEntity);
    }

    public AiCaseConfigResponse bootstrapConfigFromLegacy(String headerWorkspaceCode) {
        Long ownerUserId = CurrentUserContext.get();
        if (findByOwnerUserIdAndRoleType(ownerUserId, ROLE_GENERATOR) != null
                || findByOwnerUserIdAndRoleType(ownerUserId, ROLE_REVIEWER) != null) {
            throw new BadRequestException("Personal AI config already exists");
        }
        if (!hasLegacyConfig()) {
            throw new BadRequestException("No legacy AI config available");
        }
        cloneLegacyRoleConfig(ownerUserId, ROLE_GENERATOR);
        cloneLegacyRoleConfig(ownerUserId, ROLE_REVIEWER);
        return getConfig(headerWorkspaceCode, null);
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

    public AiReviewResult reviewGeneratedCases(String headerWorkspaceCode, ReviewAiGeneratedCasesRequest request) {
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_REVIEWER);
        AiCaseConfigEntity config = resolved.roleConfig();
        String prompt = buildGeneratedCasesReviewPrompt(config, request, false);
        return aiProviderClient.review(resolved.profile(), resolved.apiKey(), prompt);
    }

    public StreamedReviewResult streamReviewGeneratedCases(
            String headerWorkspaceCode,
            ReviewAiGeneratedCasesRequest request,
            Consumer<AiStreamModelInfo> modelConsumer,
            Consumer<ReviewCaseStreamUpdate> reviewConsumer
    ) {
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_REVIEWER);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (modelConsumer != null) {
            modelConsumer.accept(new AiStreamModelInfo(resolved.profile().provider(), config.getModel()));
        }
        String prompt = buildGeneratedCasesReviewPrompt(config, request, true);
        Map<Integer, ReviewCaseStreamUpdate> updates = new LinkedHashMap<>();
        StringBuilder rawOutput = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();
        Consumer<String> deltaConsumer = delta -> {
            rawOutput.append(delta);
            lineBuffer.append(delta);
            drainCompleteLines(lineBuffer, line -> emitReviewLine(
                    line,
                    request.generatedCases().size(),
                    rawOutput,
                    updates,
                    reviewConsumer
            ));
        };

        AiProviderClient.StreamContentResult streamResult = aiProviderClient.streamStructuredContentWithResult(
                resolved.profile(),
                resolved.apiKey(),
                prompt,
                deltaConsumer
        );
        String finalContent = streamResult.content();
        String rawContent = finalContent == null || finalContent.isBlank() ? rawOutput.toString() : finalContent;
        emitReviewLine(
                lineBuffer.toString(),
                request.generatedCases().size(),
                new StringBuilder(rawContent),
                updates,
                reviewConsumer
        );

        AiReviewResult reviewResult = buildStreamReviewResult(rawContent, updates);
        if (updates.isEmpty() && !request.generatedCases().isEmpty()) {
            emitCompleteReviewResultAsUpdates(reviewResult, rawContent, request.generatedCases().size(), updates, reviewConsumer);
        }
        return new StreamedReviewResult(
                resolved.profile().provider(),
                config.getModel(),
                reviewResult,
                rawContent,
                streamResult.fallbackToComplete(),
                streamResult.fallbackReason()
        );
    }

    public AiReviewResult reviewSavedCase(String headerWorkspaceCode, CaseDetailResponse detail) {
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_REVIEWER);
        AiCaseConfigEntity config = resolved.roleConfig();
        String prompt = buildSavedCaseReviewPrompt(config, detail);
        return aiProviderClient.review(resolved.profile(), resolved.apiKey(), prompt);
    }

    private AiCaseConfigEntity findByOwnerUserIdAndRoleType(Long ownerUserId, String roleType) {
        return aiCaseConfigMapper.selectOne(new LambdaQueryWrapper<AiCaseConfigEntity>()
                .eq(AiCaseConfigEntity::getOwnerUserId, ownerUserId)
                .eq(AiCaseConfigEntity::getRoleType, roleType)
                .last("limit 1"));
    }

    private AiCaseConfigEntity findLegacyRoleConfig(String roleType) {
        return aiCaseConfigMapper.selectOne(new LambdaQueryWrapper<AiCaseConfigEntity>()
                .isNull(AiCaseConfigEntity::getOwnerUserId)
                .eq(AiCaseConfigEntity::getWorkspaceId, PERSONAL_SCOPE_WORKSPACE_ID)
                .eq(AiCaseConfigEntity::getRoleType, roleType)
                .last("limit 1"));
    }

    private boolean hasLegacyConfig() {
        Long count = aiCaseConfigMapper.selectCount(new LambdaQueryWrapper<AiCaseConfigEntity>()
                .isNull(AiCaseConfigEntity::getOwnerUserId)
                .eq(AiCaseConfigEntity::getWorkspaceId, PERSONAL_SCOPE_WORKSPACE_ID));
        return count != null && count > 0;
    }

    private AiCaseConfigEntity requireConfig(Long id) {
        AiCaseConfigEntity entity = aiCaseConfigMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getOwnerUserId(), CurrentUserContext.get())) {
            throw new BadRequestException("AI config does not exist");
        }
        return entity;
    }

    private AiProviderConnectionEntity requireProviderConnection(Long id) {
        AiProviderConnectionEntity entity = aiProviderConnectionMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getOwnerUserId(), CurrentUserContext.get())) {
            throw new BadRequestException("AI 连接不存在");
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

    private void applyRoleRequest(
            AiCaseConfigEntity entity,
            SaveAiCaseConfigRequest request,
            AiCaseConfigEntity existing,
            boolean creating
    ) {
        entity.setRoleType(normalizeRoleType(request.roleType()));
        entity.setModel(request.model().trim());
        entity.setPromptTemplate(request.promptTemplate().trim());
        entity.setReviewChecklist(blankToNull(request.reviewChecklist()));
        entity.setTemperature(request.temperature());
        entity.setMaxCases(normalizeRoleMaxCases(request.maxCases()));
        entity.setStatus(normalizeStatus(request.status()));
        AiProviderConnectionEntity connection = resolveRequestedConnection(request, existing, creating);
        entity.setProviderConnectionId(connection.getId());
        mirrorConnectionSnapshot(entity, connection);
        AiCapabilityOverride override = mergeCapabilityOverride(request);
        entity.setCapabilityOverrideJson(writeCapabilityOverride(override));
        AiModelCapabilities detectedCapabilities = resolveDetectedCapabilities(connection, entity.getModel());
        AiModelCapabilities effectiveCapabilities = detectedCapabilities.applyOverride(override);
        entity.setSupportsImageInput(Boolean.TRUE.equals(effectiveCapabilities.imageInput().supported()) ? 1 : 0);
    }

    private AiCaseConfigItem toItem(AiCaseConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        AiProviderConnectionEntity connection = resolveBoundConnection(entity);
        String protocolType = connection != null
                ? normalizeProtocolType(connection.getProtocolType(), null, connection.getBaseUrl())
                : resolveProtocolType(entity);
        String baseUrl = connection != null ? connection.getBaseUrl() : entity.getBaseUrl();
        String apiKey = connection != null
                ? aiSecretCodec.decrypt(connection.getApiKeyCipherText())
                : aiSecretCodec.decrypt(entity.getApiKeyCipherText());
        AiCapabilityOverride override = readCapabilityOverride(entity);
        AiModelCapabilities detectedCapabilities = connection != null
                ? resolveDetectedCapabilities(connection, entity.getModel())
                : AiModelCapabilities.infer(protocolType, entity.getModel(), apiKey != null && !apiKey.isBlank());
        AiModelCapabilities effectiveCapabilities = detectedCapabilities.applyOverride(override);
        return new AiCaseConfigItem(
                entity.getId(),
                PERSONAL_SCOPE_WORKSPACE_CODE,
                PERSONAL_SCOPE_WORKSPACE_NAME,
                entity.getRoleType(),
                connection == null ? null : connection.getId(),
                connection == null ? null : connection.getConnectionName(),
                protocolType,
                providerForProtocolType(protocolType),
                entity.getModel(),
                baseUrl,
                maskApiKey(apiKey),
                apiKey != null && !apiKey.isBlank(),
                entity.getPromptTemplate(),
                entity.getReviewChecklist(),
                entity.getTemperature(),
                entity.getMaxCases(),
                detectedCapabilities,
                effectiveCapabilities,
                override,
                Boolean.TRUE.equals(effectiveCapabilities.imageInput().supported()),
                normalizeStatus(entity.getStatus())
        );
    }

    private AiProviderConnectionItem toConnectionItem(AiProviderConnectionEntity entity) {
        Long modelCount = aiProviderModelMapper.selectCount(new LambdaQueryWrapper<AiProviderModelEntity>()
                .eq(AiProviderModelEntity::getConnectionId, entity.getId()));
        String preferredModelName = resolvePreferredModelForConnection(entity.getId());
        return new AiProviderConnectionItem(
                entity.getId(),
                PERSONAL_SCOPE_WORKSPACE_CODE,
                PERSONAL_SCOPE_WORKSPACE_NAME,
                entity.getConnectionName(),
                normalizeProtocolType(entity.getProtocolType(), null, entity.getBaseUrl()),
                entity.getBaseUrl(),
                entity.getRequestTimeoutSeconds(),
                preferredModelName,
                maskApiKey(aiSecretCodec.decrypt(entity.getApiKeyCipherText())),
                entity.getApiKeyCipherText() != null && !entity.getApiKeyCipherText().isBlank(),
                normalizeStatus(entity.getStatus()),
                modelCount == null ? 0 : modelCount.intValue(),
                entity.getLastVerifiedAt(),
                entity.getLastFetchModelsAt()
        );
    }

    private AiProviderModelItem toModelItem(AiProviderModelEntity entity) {
        return new AiProviderModelItem(
                entity.getId(),
                entity.getConnectionId(),
                entity.getModelName(),
                blankToNull(entity.getDisplayName()) == null ? entity.getModelName() : entity.getDisplayName(),
                readCapabilities(entity.getDetectedCapabilitiesJson(), entity.getModelName(), null),
                entity.getSelectable() == null || entity.getSelectable() == 1,
                entity.getRawMetadataJson(),
                entity.getLastProbedAt()
        );
    }

    private void applyProviderRequest(AiProviderConnectionEntity entity, SaveAiProviderConnectionRequest request, boolean creating) {
        String connectionName = blankToNull(request.connectionName());
        String baseUrl = blankToNull(request.baseUrl());
        if (connectionName == null) {
            throw new BadRequestException("AI 连接名称不能为空");
        }
        if (baseUrl == null) {
            throw new BadRequestException("AI API URL 不能为空");
        }
        entity.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        entity.setOwnerUserId(CurrentUserContext.get());
        entity.setConnectionName(connectionName);
        entity.setProtocolType(resolveProviderProtocolType(request.protocolType(), connectionName, baseUrl, request.modelName()));
        entity.setBaseUrl(baseUrl);
        entity.setRequestTimeoutSeconds(normalizeRequestTimeoutSeconds(request.requestTimeoutSeconds()));
        entity.setSelectedModelName(blankToNull(request.modelName()));
        if (creating) {
            entity.setApiKeyCipherText(aiSecretCodec.encrypt(request.apiKey().trim()));
        } else if (blankToNull(request.apiKey()) != null) {
            entity.setApiKeyCipherText(aiSecretCodec.encrypt(request.apiKey().trim()));
        }
        entity.setStatus(normalizeStatus(request.status()));
    }

    private AiProviderConnectionEntity resolveRequestedConnection(
            SaveAiCaseConfigRequest request,
            AiCaseConfigEntity existing,
            boolean creating
    ) {
        if (request.providerConnectionId() != null) {
            return requireProviderConnection(request.providerConnectionId());
        }
        String baseUrl = blankToNull(request.baseUrl());
        if (baseUrl == null && existing != null && existing.getProviderConnectionId() != null) {
            return requireProviderConnection(existing.getProviderConnectionId());
        }
        if (baseUrl == null) {
            throw new BadRequestException("请先选择或创建 AI 连接");
        }
        String protocolType = normalizeProtocolType(request.protocolType(), request.provider(), request.baseUrl());
        String apiKey = blankToNull(request.apiKey());
        if (apiKey == null && existing != null) {
            AiProviderConnectionEntity existingConnection = resolveBoundConnection(existing);
            if (existingConnection != null) {
                apiKey = aiSecretCodec.decrypt(existingConnection.getApiKeyCipherText());
            } else if (existing.getApiKeyCipherText() != null) {
                apiKey = aiSecretCodec.decrypt(existing.getApiKeyCipherText());
            }
        }
        if (apiKey == null && creating) {
            throw new BadRequestException("AI API Key 不能为空");
        }
        AiProviderConnectionEntity matched = findMatchingConnection(CurrentUserContext.get(), protocolType, baseUrl, apiKey);
        if (matched != null) {
            return matched;
        }
        if (apiKey == null) {
            throw new BadRequestException("当前未找到可复用的 AI 连接，请先补充 API Key");
        }
        AiProviderConnectionEntity created = new AiProviderConnectionEntity();
        created.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        created.setOwnerUserId(CurrentUserContext.get());
        created.setConnectionName("角色迁移连接-" + normalizeRoleType(request.roleType()));
        created.setProtocolType(protocolType);
        created.setBaseUrl(baseUrl);
        created.setRequestTimeoutSeconds(null);
        created.setApiKeyCipherText(aiSecretCodec.encrypt(apiKey));
        created.setStatus(1);
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        aiProviderConnectionMapper.insert(created);
        return created;
    }

    private AiProviderConnectionEntity findMatchingConnection(Long ownerUserId, String protocolType, String baseUrl, String apiKey) {
        List<AiProviderConnectionEntity> candidates = aiProviderConnectionMapper.selectList(new LambdaQueryWrapper<AiProviderConnectionEntity>()
                .eq(AiProviderConnectionEntity::getOwnerUserId, ownerUserId)
                .eq(AiProviderConnectionEntity::getProtocolType, protocolType)
                .eq(AiProviderConnectionEntity::getBaseUrl, baseUrl));
        if (apiKey == null) {
            return candidates.stream().findFirst().orElse(null);
        }
        return candidates.stream()
                .filter(item -> Objects.equals(apiKey, aiSecretCodec.decrypt(item.getApiKeyCipherText())))
                .findFirst()
                .orElse(null);
    }

    private void cloneLegacyRoleConfig(Long ownerUserId, String roleType) {
        AiCaseConfigEntity legacyConfig = findLegacyRoleConfig(roleType);
        if (legacyConfig == null) {
            return;
        }
        AiProviderConnectionEntity personalConnection = cloneLegacyConnection(ownerUserId, legacyConfig);
        AiCaseConfigEntity cloned = new AiCaseConfigEntity();
        cloned.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        cloned.setOwnerUserId(ownerUserId);
        cloned.setRoleType(legacyConfig.getRoleType());
        cloned.setProtocolType(legacyConfig.getProtocolType());
        cloned.setProvider(legacyConfig.getProvider());
        cloned.setModel(legacyConfig.getModel());
        cloned.setBaseUrl(personalConnection.getBaseUrl());
        cloned.setApiKeyCipherText(personalConnection.getApiKeyCipherText());
        cloned.setPromptTemplate(legacyConfig.getPromptTemplate());
        cloned.setReviewChecklist(legacyConfig.getReviewChecklist());
        cloned.setTemperature(legacyConfig.getTemperature());
        cloned.setMaxCases(legacyConfig.getMaxCases());
        cloned.setProviderConnectionId(personalConnection.getId());
        cloned.setCapabilityOverrideJson(legacyConfig.getCapabilityOverrideJson());
        cloned.setSupportsImageInput(legacyConfig.getSupportsImageInput());
        cloned.setStatus(normalizeStatus(legacyConfig.getStatus()));
        cloned.setCreatedAt(LocalDateTime.now());
        cloned.setUpdatedAt(LocalDateTime.now());
        aiCaseConfigMapper.insert(cloned);
    }

    private AiProviderConnectionEntity cloneLegacyConnection(Long ownerUserId, AiCaseConfigEntity legacyConfig) {
        AiProviderConnectionEntity legacyConnection = resolveBoundConnection(legacyConfig);
        String protocolType = legacyConnection != null
                ? legacyConnection.getProtocolType()
                : normalizeProtocolType(legacyConfig.getProtocolType(), legacyConfig.getProvider(), legacyConfig.getBaseUrl());
        String baseUrl = legacyConnection != null ? legacyConnection.getBaseUrl() : legacyConfig.getBaseUrl();
        String apiKey = legacyConnection != null
                ? aiSecretCodec.decrypt(legacyConnection.getApiKeyCipherText())
                : aiSecretCodec.decrypt(legacyConfig.getApiKeyCipherText());
        AiProviderConnectionEntity existing = findMatchingConnection(ownerUserId, protocolType, baseUrl, apiKey);
        if (existing != null) {
            return existing;
        }
        AiProviderConnectionEntity cloned = new AiProviderConnectionEntity();
        cloned.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        cloned.setOwnerUserId(ownerUserId);
        cloned.setConnectionName((legacyConnection != null ? legacyConnection.getConnectionName() : null) == null
                ? "旧版迁移-" + legacyConfig.getRoleType()
                : legacyConnection.getConnectionName());
        cloned.setProtocolType(protocolType);
        cloned.setBaseUrl(baseUrl);
        cloned.setRequestTimeoutSeconds(legacyConnection == null ? null : legacyConnection.getRequestTimeoutSeconds());
        cloned.setApiKeyCipherText(apiKey == null ? null : aiSecretCodec.encrypt(apiKey));
        cloned.setStatus(legacyConnection != null ? normalizeStatus(legacyConnection.getStatus()) : normalizeStatus(legacyConfig.getStatus()));
        cloned.setLastVerifiedAt(legacyConnection == null ? null : legacyConnection.getLastVerifiedAt());
        cloned.setLastFetchModelsAt(legacyConnection == null ? null : legacyConnection.getLastFetchModelsAt());
        cloned.setCreatedAt(LocalDateTime.now());
        cloned.setUpdatedAt(LocalDateTime.now());
        aiProviderConnectionMapper.insert(cloned);
        if (legacyConnection != null) {
            cloneLegacyModelCache(legacyConnection.getId(), cloned.getId());
        }
        return cloned;
    }

    private void cloneLegacyModelCache(Long sourceConnectionId, Long targetConnectionId) {
        List<AiProviderModelEntity> models = aiProviderModelMapper.selectList(new LambdaQueryWrapper<AiProviderModelEntity>()
                .eq(AiProviderModelEntity::getConnectionId, sourceConnectionId));
        for (AiProviderModelEntity model : models) {
            AiProviderModelEntity cloned = new AiProviderModelEntity();
            cloned.setConnectionId(targetConnectionId);
            cloned.setModelName(model.getModelName());
            cloned.setDisplayName(model.getDisplayName());
            cloned.setRawMetadataJson(model.getRawMetadataJson());
            cloned.setDetectedCapabilitiesJson(model.getDetectedCapabilitiesJson());
            cloned.setSelectable(model.getSelectable());
            cloned.setLastProbedAt(model.getLastProbedAt());
            cloned.setCreatedAt(LocalDateTime.now());
            cloned.setUpdatedAt(LocalDateTime.now());
            aiProviderModelMapper.insert(cloned);
        }
    }

    private AiProviderConnectionEntity resolveBoundConnection(AiCaseConfigEntity entity) {
        if (entity == null || entity.getProviderConnectionId() == null) {
            return null;
        }
        return aiProviderConnectionMapper.selectById(entity.getProviderConnectionId());
    }

    private void mirrorConnectionSnapshot(AiCaseConfigEntity entity, AiProviderConnectionEntity connection) {
        String protocolType = normalizeProtocolType(connection.getProtocolType(), null, connection.getBaseUrl());
        entity.setProtocolType(protocolType);
        entity.setProvider(providerForProtocolType(protocolType));
        entity.setBaseUrl(connection.getBaseUrl());
    }

    private AiCapabilityOverride mergeCapabilityOverride(SaveAiCaseConfigRequest request) {
        AiCapabilityOverride base = request.capabilityOverride();
        if (request.supportsImageInput() == null) {
            return base;
        }
        return new AiCapabilityOverride(
                base == null ? null : base.textChat(),
                base == null ? null : base.streamOutput(),
                base == null ? null : base.structuredOutput(),
                request.supportsImageInput(),
                base == null ? null : base.longContext(),
                base == null ? null : base.stableAvailable()
        );
    }

    private String writeCapabilityOverride(AiCapabilityOverride override) {
        if (override == null || !override.hasAnyValue()) {
            return null;
        }
        return AiCaseJsonSupport.toJson(override, "AI 能力覆盖配置序列化失败");
    }

    private AiCapabilityOverride readCapabilityOverride(AiCaseConfigEntity entity) {
        AiCapabilityOverride override = AiCaseJsonSupport.read(entity.getCapabilityOverrideJson(), AiCapabilityOverride.class, null);
        if (override != null) {
            return override;
        }
        if (entity.getSupportsImageInput() == null) {
            return null;
        }
        return new AiCapabilityOverride(null, null, null, entity.getSupportsImageInput() == 1, null, null);
    }

    private String writeCapabilities(AiModelCapabilities capabilities) {
        return AiCaseJsonSupport.toJson(capabilities, "AI 模型能力序列化失败");
    }

    private AiModelCapabilities readCapabilities(String json, String modelName, String protocolType) {
        return AiCaseJsonSupport.read(
                json,
                AiModelCapabilities.class,
                AiModelCapabilities.infer(protocolType == null ? PROTOCOL_OPENAI_CHAT : protocolType, modelName, false)
        );
    }

    private AiModelCapabilities resolveDetectedCapabilities(AiProviderConnectionEntity connection, String modelName) {
        AiProviderModelEntity cache = findProviderModelByName(connection.getId(), modelName);
        if (cache != null) {
            return readCapabilities(cache.getDetectedCapabilitiesJson(), modelName, connection.getProtocolType());
        }
        return AiModelCapabilities.infer(connection.getProtocolType(), modelName, true);
    }

    private boolean supportsImageInputForGeneration(ResolvedRoleConfig resolved) {
        AiCapabilityValue effectiveImage = resolved.effectiveCapabilities().imageInput();
        if (Boolean.FALSE.equals(effectiveImage.supported())) {
            return false;
        }
        return Boolean.TRUE.equals(resolved.detectedCapabilities().imageInput().supported());
    }

    private List<AiProviderModelItem> persistFetchedModels(
            AiProviderConnectionEntity connection,
            List<AiProviderModelItem> fetchedModels,
            LocalDateTime fetchedAt
    ) {
        Map<String, AiProviderModelEntity> existingByName = aiProviderModelMapper.selectList(new LambdaQueryWrapper<AiProviderModelEntity>()
                        .eq(AiProviderModelEntity::getConnectionId, connection.getId()))
                .stream()
                .collect(Collectors.toMap(AiProviderModelEntity::getModelName, item -> item));
        List<Long> keepIds = new ArrayList<>();
        List<AiProviderModelItem> result = new ArrayList<>();
        for (AiProviderModelItem model : fetchedModels) {
            AiProviderModelEntity entity = existingByName.get(model.modelName());
            if (entity == null) {
                entity = new AiProviderModelEntity();
                entity.setConnectionId(connection.getId());
                entity.setModelName(model.modelName());
                entity.setCreatedAt(fetchedAt);
            }
            entity.setDisplayName(blankToNull(model.displayName()) == null ? model.modelName() : model.displayName());
            entity.setRawMetadataJson(model.rawMetadataJson());
            entity.setDetectedCapabilitiesJson(writeCapabilities(model.detectedCapabilities()));
            entity.setSelectable(model.selectable() ? 1 : 0);
            entity.setUpdatedAt(fetchedAt);
            if (entity.getId() == null) {
                aiProviderModelMapper.insert(entity);
            } else {
                aiProviderModelMapper.updateById(entity);
            }
            keepIds.add(entity.getId());
            result.add(toModelItem(entity));
        }
        if (!keepIds.isEmpty()) {
            List<AiProviderModelEntity> stale = existingByName.values().stream()
                    .filter(item -> item.getId() != null && !keepIds.contains(item.getId()))
                    .toList();
            stale.forEach(item -> aiProviderModelMapper.deleteById(item.getId()));
        }
        return result.stream()
                .sorted(Comparator.comparing(AiProviderModelItem::modelName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<AiProviderModelItem> listModelItems(Long connectionId) {
        return aiProviderModelMapper.selectList(new LambdaQueryWrapper<AiProviderModelEntity>()
                        .eq(AiProviderModelEntity::getConnectionId, connectionId)
                        .orderByAsc(AiProviderModelEntity::getDisplayName)
                        .orderByAsc(AiProviderModelEntity::getModelName))
                .stream()
                .map(this::toModelItem)
                .toList();
    }

    private AiProviderModelEntity findProviderModelByName(Long connectionId, String modelName) {
        return aiProviderModelMapper.selectOne(new LambdaQueryWrapper<AiProviderModelEntity>()
                .eq(AiProviderModelEntity::getConnectionId, connectionId)
                .eq(AiProviderModelEntity::getModelName, modelName)
                .last("limit 1"));
    }

    private void syncRequestedModel(AiProviderConnectionEntity connection, String modelName) {
        String normalizedModelName = blankToNull(modelName);
        if (connection == null || connection.getId() == null || normalizedModelName == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AiProviderModelEntity modelEntity = findProviderModelByName(connection.getId(), normalizedModelName);
        if (modelEntity == null) {
            modelEntity = new AiProviderModelEntity();
            modelEntity.setConnectionId(connection.getId());
            modelEntity.setModelName(normalizedModelName);
            modelEntity.setDisplayName(normalizedModelName);
            modelEntity.setSelectable(1);
            modelEntity.setCreatedAt(now);
        }
        if (blankToNull(modelEntity.getDisplayName()) == null) {
            modelEntity.setDisplayName(normalizedModelName);
        }
        modelEntity.setSelectable(1);
        modelEntity.setLastProbedAt(now);
        modelEntity.setUpdatedAt(now);
        if (modelEntity.getId() == null) {
            aiProviderModelMapper.insert(modelEntity);
        } else {
            aiProviderModelMapper.updateById(modelEntity);
        }
    }

    private String resolvePreferredModelForConnection(Long connectionId) {
        AiProviderConnectionEntity connection = aiProviderConnectionMapper.selectById(connectionId);
        if (connection != null && blankToNull(connection.getSelectedModelName()) != null) {
            return connection.getSelectedModelName();
        }
        AiProviderModelEntity cachedModel = aiProviderModelMapper.selectOne(new LambdaQueryWrapper<AiProviderModelEntity>()
                .eq(AiProviderModelEntity::getConnectionId, connectionId)
                .orderByDesc(AiProviderModelEntity::getLastProbedAt)
                .orderByAsc(AiProviderModelEntity::getModelName)
                .last("limit 1"));
        if (cachedModel != null && blankToNull(cachedModel.getModelName()) != null) {
            return cachedModel.getModelName();
        }
        AiCaseConfigEntity boundRole = aiCaseConfigMapper.selectOne(new LambdaQueryWrapper<AiCaseConfigEntity>()
                .eq(AiCaseConfigEntity::getOwnerUserId, CurrentUserContext.get())
                .eq(AiCaseConfigEntity::getProviderConnectionId, connectionId)
                .orderByDesc(AiCaseConfigEntity::getUpdatedAt)
                .last("limit 1"));
        return boundRole == null ? null : boundRole.getModel();
    }

    private ResolvedRoleConfig requireResolvedRoleConfig(String roleType) {
        AiCaseConfigEntity roleConfig = findByOwnerUserIdAndRoleType(CurrentUserContext.get(), roleType);
        if (roleConfig == null || normalizeStatus(roleConfig.getStatus()) != 1) {
            throw new BadRequestException("No active personal " + roleType + " config found");
        }
        AiProviderConnectionEntity connection = resolveBoundConnection(roleConfig);
        String apiKey;
        AiProviderRequestProfile profile;
        if (connection != null) {
            apiKey = requireProviderApiKey(connection);
            profile = buildProviderProfile(connection, roleConfig.getModel(), roleConfig.getTemperature(), roleConfig.getMaxCases());
        } else {
            apiKey = requireConfigApiKey(roleConfig);
            profile = buildLegacyProfile(roleType, new SaveAiCaseConfigRequest(
                    roleConfig.getWorkspaceId() == null ? null : PERSONAL_SCOPE_WORKSPACE_CODE,
                    roleConfig.getRoleType(),
                    null,
                    roleConfig.getProtocolType(),
                    roleConfig.getProvider(),
                    roleConfig.getModel(),
                    roleConfig.getBaseUrl(),
                    apiKey,
                    roleConfig.getPromptTemplate(),
                    roleConfig.getReviewChecklist(),
                    roleConfig.getTemperature(),
                    roleConfig.getMaxCases(),
                    readCapabilityOverride(roleConfig),
                    roleConfig.getSupportsImageInput() != null && roleConfig.getSupportsImageInput() == 1,
                    roleConfig.getStatus()
            ), roleConfig);
        }
        AiCapabilityOverride override = readCapabilityOverride(roleConfig);
        AiModelCapabilities detectedCapabilities = connection != null
                ? resolveDetectedCapabilities(connection, roleConfig.getModel())
                : AiModelCapabilities.infer(profile.protocolType(), roleConfig.getModel(), true);
        AiModelCapabilities effectiveCapabilities = detectedCapabilities.applyOverride(override);
        return new ResolvedRoleConfig(roleConfig, connection, profile, apiKey, detectedCapabilities, effectiveCapabilities);
    }

    private String requireProviderApiKey(AiProviderConnectionEntity connection) {
        String apiKey = aiSecretCodec.decrypt(connection.getApiKeyCipherText());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI 连接未配置 API Key");
        }
        return apiKey;
    }

    private String requireConfigApiKey(AiCaseConfigEntity config) {
        String decryptedApiKey = aiSecretCodec.decrypt(config.getApiKeyCipherText());
        if (decryptedApiKey == null || decryptedApiKey.isBlank()) {
            throw new BadRequestException("AI config API key is missing");
        }
        return decryptedApiKey;
    }

    private AiProviderRequestProfile buildProviderProfile(
            AiProviderConnectionEntity connection,
            String model,
            Double temperature,
            Integer maxCases
    ) {
        String protocolType = resolveProviderProtocolType(
                connection.getProtocolType(),
                connection.getConnectionName(),
                connection.getBaseUrl(),
                model
        );
        return new AiProviderRequestProfile(
                protocolType,
                providerForProtocolType(protocolType),
                model,
                connection.getBaseUrl(),
                temperature == null ? 0.3 : temperature,
                maxCases,
                resolveRequestTimeoutSeconds(connection.getRequestTimeoutSeconds())
        );
    }

    private AiProviderRequestProfile buildPreviewProviderProfile(PreviewAiProviderModelsRequest request) {
        String baseUrl = blankToNull(request.baseUrl());
        if (baseUrl == null) {
            throw new BadRequestException("AI API URL 不能为空");
        }
        String protocolType = resolveProviderProtocolType(request.protocolType(), null, baseUrl, null);
        return new AiProviderRequestProfile(
                protocolType,
                providerForProtocolType(protocolType),
                "model-probe",
                baseUrl,
                0.3,
                null,
                resolveRequestTimeoutSeconds(request.requestTimeoutSeconds())
        );
    }

    private AiProviderRequestProfile buildLegacyProfile(
            String roleType,
            SaveAiCaseConfigRequest request,
            AiCaseConfigEntity existing
    ) {
        String protocolType = normalizeProtocolType(request.protocolType(), request.provider(), request.baseUrl());
        String baseUrl = blankToNull(request.baseUrl());
        if (baseUrl == null && existing != null) {
            baseUrl = existing.getBaseUrl();
        }
        if (baseUrl == null) {
            throw new BadRequestException("AI API URL 不能为空");
        }
        Double temperature = request.temperature() == null
                ? (existing == null ? 0.3 : existing.getTemperature())
                : request.temperature();
        Integer maxCases = request.maxCases() == null
                ? (existing == null ? INITIAL_SMART_MAX_CASES : existing.getMaxCases())
                : request.maxCases();
        return new AiProviderRequestProfile(
                protocolType,
                providerForProtocolType(protocolType),
                request.model().trim(),
                baseUrl,
                temperature,
                maxCases,
                defaultRequestTimeoutSeconds
        );
    }

    private String buildGeneratorPrompt(
            AiCaseConfigEntity config,
            GenerateAiCasesRequest request,
            WorkspaceEntity workspace,
            int maxCases,
            List<AiRequirementAssetEntity> assets,
            boolean streamMode
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
        builder.append("[Smart Generation Policy]\n");
        builder.append("- Generate enough high-value cases according to requirement complexity.\n");
        builder.append("- Do not pad duplicated or low-value cases just to reach a number.\n");
        builder.append("- Initial generation must return at most ").append(maxCases).append(" cases.\n");
        builder.append("- If fewer cases are enough, return fewer cases.\n");
        builder.append("- If full coverage needs more than ").append(maxCases).append(" cases, return the highest-value cases first and report remainingCoverageGaps.\n");
        if (config.getReviewChecklist() != null && !config.getReviewChecklist().isBlank()) {
            builder.append("[Extra Checklist]\n").append(config.getReviewChecklist().trim()).append("\n\n");
        }
        if (streamMode) {
            builder.append("""
                    [Output Requirements]
                    1. Return NDJSON only. Do not return markdown, explanation, JSON array wrappers, or extra prose.
                    2. Output one complete JSON object per line. Each line represents one finished test case.
                    3. Every line must contain:
                       - title
                       - caseType
                       - priority
                       - precondition
                       - steps
                       - expectedResult
                       - riskNotes
                       - testAngle: one of 正常场景, 异常场景, 边界值, 等价类, 状态迁移, 组合/判定表, 错误推测, 端到端, 非功能, 数据依赖与清理
                       - generationReason: short reason explaining why this case is needed and what risk or coverage gap it targets
                       - requirementEvidence: requirement text, business rule, constraint, or image/prototype evidence that supports this case
                    4. caseType must be one of: FUNCTION, BOUNDARY, EXCEPTION, REGRESSION.
                    5. priority must be one of: P0, P1, P2, P3.
                    6. requirementEvidence must explain the source of the case:
                       - If based on requirement text, quote or summarize the related sentence, rule, or constraint.
                       - If based on an attached image/prototype, start with "图片素材显示：".
                       - If inferred from testing risk because the requirement is not explicit, start with "需求未明确，基于风险推断：".
                       - Do not fabricate exact requirement wording. If unsure, summarize instead of quoting.
                    7. Keep titles, steps, expected results, generationReason, and requirementEvidence concrete, executable, and verifiable.
                    8. Cover useful test points first. Do not pad with duplicate or low-value cases.
                    9. If the requirement only supports fewer valid cases than the limit, return only the reasonable count.
                    10. When text and images both provide information, combine them and do not ignore key UI or flow details.
                    """);
        } else {
            builder.append("""
                    [Output Requirements]
                    1. Return JSON only. Do not return markdown, explanation, or extra prose.
                    2. The response must be:
                       {
                         "coverageSummary":"short summary of covered functions, risks, boundaries, and scenario types",
                         "remainingCoverageGaps":["gap that could not fit in the initial limit"],
                         "cases":[...]
                       }
                    3. Every case must contain:
                       - title
                       - caseType
                       - priority
                       - precondition
                       - steps
                       - expectedResult
                       - riskNotes
                       - testAngle: one of 正常场景, 异常场景, 边界值, 等价类, 状态迁移, 组合/判定表, 错误推测, 端到端, 非功能, 数据依赖与清理
                       - generationReason: short reason explaining why this case is needed and what risk or coverage gap it targets
                       - requirementEvidence: requirement text, business rule, constraint, or image/prototype evidence that supports this case
                    4. caseType must be one of: FUNCTION, BOUNDARY, EXCEPTION, REGRESSION.
                    5. priority must be one of: P0, P1, P2, P3.
                    6. requirementEvidence must explain the source of the case:
                       - If based on requirement text, quote or summarize the related sentence, rule, or constraint.
                       - If based on an attached image/prototype, start with "图片素材显示：".
                       - If inferred from testing risk because the requirement is not explicit, start with "需求未明确，基于风险推断：".
                       - Do not fabricate exact requirement wording. If unsure, summarize instead of quoting.
                    7. Keep titles, steps, expected results, generationReason, and requirementEvidence concrete, executable, and verifiable.
                    8. Cover useful test points first. Do not pad with duplicate or low-value cases.
                    9. If the requirement only supports fewer valid cases than the limit, return only the reasonable count.
                    10. When text and images both provide information, combine them and do not ignore key UI or flow details.
                    """);
        }
        return builder.toString();
    }

    private String buildGeneratedCasesReviewPrompt(AiCaseConfigEntity config, ReviewAiGeneratedCasesRequest request, boolean streamMode) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getPromptTemplate()).append("\n\n");
        builder.append("[Requirement Title] ").append(request.requirementTitle().trim()).append('\n');
        builder.append("[Requirement Content]\n").append(request.requirementContent().trim()).append("\n\n");
        if (blankToNull(request.sceneFocus()) != null) {
            builder.append("[Focus] ").append(request.sceneFocus().trim()).append('\n');
        }
        if (request.remainingCoverageGaps() != null && !request.remainingCoverageGaps().isEmpty()) {
            builder.append("[Remaining Coverage Gaps Reported By Generator]\n");
            for (String gap : request.remainingCoverageGaps()) {
                if (blankToNull(gap) != null) {
                    builder.append("- ").append(gap.trim()).append('\n');
                }
            }
            builder.append("Use these gaps as input, but independently verify coverage against the requirement.\n\n");
        }
        builder.append("[Candidate Cases To Review]\n");
        int index = 0;
        for (AiExistingCaseItem item : request.generatedCases()) {
            builder.append("[Index ").append(index++).append("] Title: ").append(nullSafe(item.title())).append('\n');
            builder.append("   Type: ").append(nullSafe(item.caseType()))
                    .append(", Priority: ").append(nullSafe(item.priority())).append('\n');
            builder.append("   Precondition: ").append(nullSafe(item.precondition())).append('\n');
            builder.append("   Steps: ").append(nullSafe(item.steps())).append('\n');
            builder.append("   Expected Result: ").append(nullSafe(item.expectedResult())).append('\n');
            builder.append("   Test Angle: ").append(nullSafe(item.testAngle())).append('\n');
            builder.append("   Generation Reason: ").append(nullSafe(item.generationReason())).append('\n');
            builder.append("   Requirement Evidence: ").append(nullSafe(item.requirementEvidence())).append("\n\n");
        }
        if (config.getReviewChecklist() != null && !config.getReviewChecklist().isBlank()) {
            builder.append("[Extra Review Checklist]\n").append(config.getReviewChecklist().trim()).append("\n\n");
        }
        if (streamMode) {
            builder.append("""
                    [Output Requirements]
                    1. Return NDJSON only. Do not return markdown, explanation, JSON array wrappers, or extra prose.
                    2. You have the full candidate case set above. First evaluate overall coverage, duplicates, gaps, and priorities internally, then output results line by line.
                    3. Output one complete JSON object per line. Flush each line immediately after the decision is ready. Do not wait until all lines are complete before emitting.
                    4. Output reviewed existing-case lines in ascending caseIndex order, then output any supplement lines.
                    5. Reviewed existing-case lines must contain:
                       - caseIndex: the zero-based Index shown above. This is an internal mapping key only.
                       - status: APPROVED, OPTIMIZED, CONFIRM_REQUIRED, or NOT_RECOMMENDED
                       - summary: one short actionable review summary. Do not mention internal labels such as "Index 0", "caseIndex", or "itemIndex"; refer to the case title or user-facing case number when needed.
                       - coverageComment: explain whether this case covers the intended requirement, risk, boundary, or scenario
                       - evidenceComment: judge whether requirementEvidence clearly maps to requirement text, business rule, image/prototype information, or a reasonable risk-based inference
                       - reviewComment: final quality judgment for this case
                       - optimizationReason: required when status is OPTIMIZED
                       - optimizedCase: required when status is OPTIMIZED, containing the full improved case fields
                       - coverageGap: optional gap this case relates to
                    6. Supplement lines must contain:
                       - status: SUPPLEMENTED
                       - summary: one short reason for adding the case
                       - supplementCase: the full new case fields
                       - supplementReason: what missing coverage this case fills
                       - coverageGap: the gap being covered
                    7. Use APPROVED when the case can be kept unchanged.
                    8. Use OPTIMIZED when the case is valuable but should be rewritten; include optimizedCase.
                    9. Do not use OPTIMIZED without optimizedCase. If you cannot provide a full rewritten case, use CONFIRM_REQUIRED.
                    10. Use CONFIRM_REQUIRED only when requirement ambiguity prevents a reliable automatic rewrite.
                    11. Use NOT_RECOMMENDED when the case is duplicated, low-value, unexecutable, or misaligned.
                    12. Add SUPPLEMENTED lines only for important missing coverage. Do not pad the count.
                    """);
        } else {
            builder.append("""
                    [Output Requirements]
                    1. Return JSON only. Do not return markdown, explanation, or extra prose.
                    2. The response must be:
                       {
                         \"result\":\"APPROVE|REJECT|SUGGEST\",
                         \"summary\":\"one-sentence summary\",
                         \"issues\":[\"issue 1\",\"issue 2\"],
                         \"suggestions\":[\"suggestion 1\",\"suggestion 2\"],
                         \"caseDecisions\":[{
                           \"caseIndex\":0,
                           \"status\":\"APPROVED|OPTIMIZED|CONFIRM_REQUIRED|NOT_RECOMMENDED\",
                           \"summary\":\"short summary\",
                           \"coverageComment\":\"coverage judgment\",
                           \"evidenceComment\":\"evidence judgment\",
                           \"reviewComment\":\"quality judgment\",
                           \"optimizationReason\":\"why optimized, required for OPTIMIZED\",
                           \"coverageGap\":\"related gap if any\",
                           \"optimizedCase\":{ \"title\":\"...\", \"caseType\":\"FUNCTION|BOUNDARY|EXCEPTION|REGRESSION\", \"priority\":\"P0|P1|P2|P3\", \"precondition\":\"...\", \"steps\":\"...\", \"expectedResult\":\"...\", \"riskNotes\":\"...\", \"testAngle\":\"...\", \"generationReason\":\"...\", \"requirementEvidence\":\"...\" }
                         }],
                         \"supplementCases\":[{
                           \"title\":\"...\",
                           \"caseType\":\"FUNCTION|BOUNDARY|EXCEPTION|REGRESSION\",
                           \"priority\":\"P0|P1|P2|P3\",
                           \"precondition\":\"...\",
                           \"steps\":\"...\",
                           \"expectedResult\":\"...\",
                           \"riskNotes\":\"...\",
                           \"testAngle\":\"...\",
                           \"generationReason\":\"why this supplement is needed\",
                           \"requirementEvidence\":\"requirement text, image/prototype evidence, or risk inference\",
                           \"supplementReason\":\"what missing coverage this case fills\",
                           \"coverageGap\":\"the gap being covered\"
                         }],
                         \"unresolvedCoverageGaps\":[\"gap still not covered because of ambiguity or final limit\"]
                       }
                    3. Use issues to point out missing coverage, duplicates, ambiguity, or non-executable content.
                    4. Review must directly optimize useful weak cases and supplement important missing cases.
                    5. Do not add low-value supplement cases. Total final cases should stay within the product limit.
                    """);
        }
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

    private String normalizeProtocolType(String protocolType, String provider, String baseUrl) {
        String normalized = protocolType == null ? "" : protocolType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!normalized.isEmpty()) {
            return switch (normalized) {
                case "OPENAI_CHAT_COMPLETIONS", PROTOCOL_OPENAI_CHAT -> PROTOCOL_OPENAI_CHAT;
                case "OPENAI_RESPONSES", PROTOCOL_OPENAI_RESPONSES -> PROTOCOL_OPENAI_RESPONSES;
                case PROTOCOL_AZURE_OPENAI -> PROTOCOL_AZURE_OPENAI;
                default -> throw new BadRequestException("AI protocol type is invalid");
            };
        }
        return mapLegacyProviderToProtocolType(provider, baseUrl);
    }

    private String resolveProviderProtocolType(String protocolType, String connectionName, String baseUrl, String modelName) {
        if (blankToNull(protocolType) != null) {
            return normalizeProtocolType(protocolType, null, baseUrl);
        }
        String source = String.join(" ",
                blankToNull(connectionName) == null ? "" : connectionName,
                blankToNull(baseUrl) == null ? "" : baseUrl,
                blankToNull(modelName) == null ? "" : modelName
        ).toLowerCase(Locale.ROOT);
        if (source.contains("azure") || source.contains(".openai.azure.com")) {
            return PROTOCOL_AZURE_OPENAI;
        }
        if (source.contains("/responses")) {
            return PROTOCOL_OPENAI_RESPONSES;
        }
        return PROTOCOL_OPENAI_CHAT;
    }

    private String resolveProtocolType(AiCaseConfigEntity entity) {
        return normalizeProtocolType(entity.getProtocolType(), entity.getProvider(), entity.getBaseUrl());
    }

    private String mapLegacyProviderToProtocolType(String provider, String baseUrl) {
        String normalizedProvider = normalizeProvider(provider);
        if ("AZURE_OPENAI".equals(normalizedProvider)) {
            return PROTOCOL_AZURE_OPENAI;
        }
        if ("INTERNAL_PROXY".equals(normalizedProvider)) {
            String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
            return normalizedBaseUrl.contains("/responses") ? PROTOCOL_OPENAI_RESPONSES : PROTOCOL_OPENAI_CHAT;
        }
        return PROTOCOL_OPENAI_CHAT;
    }

    private String providerForProtocolType(String protocolType) {
        return switch (protocolType) {
            case PROTOCOL_OPENAI_RESPONSES -> "OPENAI_COMPATIBLE_RESPONSES";
            case PROTOCOL_AZURE_OPENAI -> "AZURE_OPENAI";
            default -> "OPENAI_COMPATIBLE_CHAT";
        };
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

    private Integer normalizeRoleMaxCases(Integer maxCases) {
        if (maxCases == null) {
            return INITIAL_SMART_MAX_CASES;
        }
        if (maxCases < 1 || maxCases > 100) {
            throw new BadRequestException("Max cases must be between 1 and 100");
        }
        return Math.min(maxCases, INITIAL_SMART_MAX_CASES);
    }

    private Integer normalizeRequestTimeoutSeconds(Integer requestTimeoutSeconds) {
        if (requestTimeoutSeconds == null) {
            return null;
        }
        if (requestTimeoutSeconds < 10 || requestTimeoutSeconds > 600) {
            throw new BadRequestException("AI request timeout must be between 10 and 600 seconds");
        }
        return requestTimeoutSeconds;
    }

    private int resolveRequestTimeoutSeconds(Integer requestTimeoutSeconds) {
        Integer normalized = normalizeRequestTimeoutSeconds(requestTimeoutSeconds);
        return normalized == null ? defaultRequestTimeoutSeconds : normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String nullSafe(String value) {
        return blankToNull(value) == null ? "-" : value.trim();
    }

    private String normalizeCaseType(String caseType) {
        if (caseType == null || caseType.isBlank()) {
            return "FUNCTION";
        }
        String normalized = caseType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FUNCTION", "BOUNDARY", "EXCEPTION", "REGRESSION" -> normalized;
            default -> "FUNCTION";
        };
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "P1";
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "P1", "P2", "P3" -> normalized;
            default -> "P1";
        };
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

    private boolean isImageInputUnsupportedError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("不支持图片")
                || normalized.contains("图片输入")
                || normalized.contains("image input")
                || normalized.contains("vision")
                || normalized.contains("image_url")
                || normalized.contains("input_image");
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

    private void drainCompleteLines(StringBuilder buffer, Consumer<String> lineConsumer) {
        int index = indexOfLineBreak(buffer);
        while (index >= 0) {
            String line = buffer.substring(0, index);
            buffer.delete(0, index + 1);
            lineConsumer.accept(line);
            index = indexOfLineBreak(buffer);
        }
    }

    private int indexOfLineBreak(StringBuilder buffer) {
        for (int index = 0; index < buffer.length(); index += 1) {
            char current = buffer.charAt(index);
            if (current == '\n') {
                return index;
            }
        }
        return -1;
    }

    private void emitGeneratedCaseLine(
            String rawLine,
            int maxCases,
            List<GeneratedAiCaseItem> generatedCases,
            List<String> warnings,
            List<AiInvalidCaseItem> invalidCases,
            StringBuilder rawOutput,
            Consumer<GeneratedCaseStreamUpdate> caseConsumer
    ) {
        if (generatedCases.size() >= maxCases) {
            return;
        }
        String line = normalizeStreamJsonLine(rawLine);
        if (line == null) {
            return;
        }
        try {
            AiGeneratedCasesResult parsed = aiProviderClient.parseGeneratedCasesContent("{\"cases\":[" + line + "]}", 1);
            if (parsed.generatedCases().isEmpty()) {
                return;
            }
            warnings.addAll(parsed.warnings());
            invalidCases.addAll(parsed.invalidCases());
            GeneratedAiCaseItem item = parsed.generatedCases().get(0);
            generatedCases.add(item);
            if (caseConsumer != null) {
                caseConsumer.accept(new GeneratedCaseStreamUpdate(
                        generatedCases.size() - 1,
                        item,
                        rawOutput.toString()
                ));
            }
        } catch (RuntimeException ignored) {
            // Wait for a later complete line or final full-output fallback.
        }
    }

    private void emitReviewLine(
            String rawLine,
            int caseCount,
            StringBuilder rawOutput,
            Map<Integer, ReviewCaseStreamUpdate> updates,
            Consumer<ReviewCaseStreamUpdate> reviewConsumer
    ) {
        String line = normalizeStreamJsonLine(rawLine);
        if (line == null) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(line);
            String status = normalizePerCaseReviewStatus(firstText(root, "status", "result", "reviewStatus"));
            String summary = firstText(root, "summary", "message", "reason", "suggestion");
            String coverageComment = firstText(root, "coverageComment", "coverage", "coverageReason");
            String evidenceComment = firstText(root, "evidenceComment", "evidence", "evidenceReason");
            String reviewComment = firstText(root, "reviewComment", "comment");
            String optimizationReason = firstText(root, "optimizationReason");
            String supplementReason = firstText(root, "supplementReason");
            String coverageGap = firstText(root, "coverageGap");
            GeneratedAiCaseItem supplementCase = parseStreamGeneratedCase(firstPresentNode(root, "supplementCase", "case", "newCase"), "REVIEW_SUPPLEMENTED", "SUPPLEMENTED");
            if ("SUPPLEMENTED".equals(status)) {
                if (supplementCase == null) {
                    return;
                }
                if (summary == null || summary.isBlank()) {
                    summary = firstNonBlank(supplementReason, coverageGap, "AI review supplemented a missing case.");
                }
                ReviewCaseStreamUpdate update = new ReviewCaseStreamUpdate(null, status, summary, coverageComment, evidenceComment, reviewComment, optimizationReason, supplementReason, coverageGap, null, supplementCase, rawOutput.toString());
                updates.put(-(updates.size() + 1), update);
                if (reviewConsumer != null) {
                    reviewConsumer.accept(update);
                }
                return;
            }
            Integer caseIndex = parseReviewCaseIndex(root, caseCount);
            if (caseIndex == null) {
                return;
            }
            GeneratedAiCaseItem optimizedCase = parseStreamGeneratedCase(root.path("optimizedCase"), "REVIEW_OPTIMIZED", status);
            if (summary == null || summary.isBlank()) {
                summary = switch (status) {
                    case "APPROVED" -> "AI review approved this case.";
                    case "NOT_RECOMMENDED", "REJECTED" -> "AI review does not recommend this case.";
                    case "OPTIMIZED" -> "AI review optimized this case.";
                    default -> "AI review suggests confirming this case.";
                };
            }
            if (coverageComment == null || coverageComment.isBlank()) {
                coverageComment = summary;
            }
            if (evidenceComment == null || evidenceComment.isBlank()) {
                evidenceComment = firstText(root, "reason", "summary");
            }
            ReviewCaseStreamUpdate update = new ReviewCaseStreamUpdate(caseIndex, status, summary, coverageComment, evidenceComment, reviewComment, optimizationReason, null, coverageGap, optimizedCase, null, rawOutput.toString());
            updates.put(caseIndex, update);
            if (reviewConsumer != null) {
                reviewConsumer.accept(update);
            }
        } catch (Exception ignored) {
            // Wait for a later complete line or final full-output fallback.
        }
    }

    private String normalizeStreamJsonLine(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.trim();
        while (line.startsWith(",")) {
            line = line.substring(1).trim();
        }
        while (line.endsWith(",")) {
            line = line.substring(0, line.length() - 1).trim();
        }
        if (line.isBlank() || line.startsWith("```") || line.startsWith("[") || !line.startsWith("{")) {
            return null;
        }
        return line;
    }

    private Integer parseReviewCaseIndex(JsonNode root, int caseCount) {
        Integer caseIndex = optionalInt(root.path("caseIndex"));
        if (caseIndex != null && caseIndex >= 0 && caseIndex < caseCount) {
            return caseIndex;
        }
        Integer itemIndex = optionalInt(root.path("itemIndex"));
        if (itemIndex != null && itemIndex >= 0 && itemIndex < caseCount) {
            return itemIndex;
        }
        Integer index = optionalInt(root.path("index"));
        if (index != null && index >= 0 && index < caseCount) {
            return index;
        }
        Integer caseNo = optionalInt(root.path("caseNo"));
        if (caseNo != null && caseNo >= 1 && caseNo <= caseCount) {
            return caseNo - 1;
        }
        return null;
    }

    private GeneratedAiCaseItem parseStreamGeneratedCase(JsonNode node, String source, String reviewStatus) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String title = firstText(node, "title");
        String steps = firstText(node, "steps");
        String expectedResult = firstText(node, "expectedResult");
        if (title == null || steps == null || expectedResult == null) {
            return null;
        }
        return new GeneratedAiCaseItem(
                title,
                normalizeCaseType(firstText(node, "caseType")),
                normalizePriority(firstText(node, "priority")),
                firstText(node, "precondition"),
                steps,
                expectedResult,
                firstText(node, "riskNotes"),
                firstText(node, "testAngle"),
                firstText(node, "generationReason"),
                firstText(node, "requirementEvidence"),
                firstText(node, "aiSource", "source") == null ? source : firstText(node, "aiSource", "source"),
                firstText(node, "reviewComment"),
                firstText(node, "optimizationReason"),
                firstText(node, "supplementReason"),
                firstText(node, "coverageGap"),
                null,
                List.of(),
                firstText(node, "aiReviewStatus", "reviewStatus") == null ? reviewStatus : firstText(node, "aiReviewStatus", "reviewStatus"),
                firstText(node, "aiReviewSummary", "reviewSummary"),
                false,
                null,
                null
        );
    }

    private GeneratedAiCaseItem withSupplementReviewMetadata(GeneratedAiCaseItem item, String summary, String supplementReason, String coverageGap) {
        return new GeneratedAiCaseItem(
                item.title(),
                item.caseType(),
                item.priority(),
                item.precondition(),
                item.steps(),
                item.expectedResult(),
                item.riskNotes(),
                item.testAngle(),
                item.generationReason(),
                item.requirementEvidence(),
                "REVIEW_SUPPLEMENTED",
                item.reviewComment(),
                item.optimizationReason(),
                firstNonBlank(item.supplementReason(), supplementReason),
                firstNonBlank(item.coverageGap(), coverageGap),
                null,
                item.warnings(),
                "SUPPLEMENTED",
                firstNonBlank(item.aiReviewSummary(), summary, supplementReason, coverageGap),
                item.manualEdited(),
                item.manualEditedByName(),
                item.manualEditedAt()
        );
    }

    private Integer optionalInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.canConvertToInt()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (node.isTextual() && !node.asText().trim().isBlank()) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstPresentNode(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.path(fieldName);
            if (!node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String normalizePerCaseReviewStatus(String status) {
        if (status == null || status.isBlank()) {
            return "CONFIRM_REQUIRED";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVE", "APPROVED", "PASS", "PASSED" -> "APPROVED";
            case "OPTIMIZE", "OPTIMIZED", "SUGGESTED", "SUGGEST", "IMPROVED" -> "OPTIMIZED";
            case "SUPPLEMENT", "SUPPLEMENTED", "ADDED" -> "SUPPLEMENTED";
            case "CONFIRM", "CONFIRM_REQUIRED", "NEEDS_CONFIRMATION" -> "CONFIRM_REQUIRED";
            case "NOT_RECOMMENDED", "REJECT", "REJECTED", "FAIL", "FAILED" -> "NOT_RECOMMENDED";
            default -> "CONFIRM_REQUIRED";
        };
    }

    private AiReviewResult buildStreamReviewResult(String rawContent, Map<Integer, ReviewCaseStreamUpdate> updates) {
        if (updates.isEmpty()) {
            return aiProviderClient.parseReviewResultContent(rawContent);
        }
        boolean hasRejected = updates.values().stream().anyMatch(item -> "NOT_RECOMMENDED".equals(item.status()));
        boolean hasSuggested = updates.values().stream().anyMatch(item -> !"APPROVED".equals(item.status()));
        String result = hasRejected ? "REJECT" : hasSuggested ? "SUGGEST" : "APPROVE";
        List<String> issues = updates.values().stream()
                .filter(item -> "NOT_RECOMMENDED".equals(item.status()))
                .map(item -> "Case " + (item.itemIndex() + 1) + ": " + item.summary())
                .toList();
        List<String> suggestions = updates.values().stream()
                .filter(item -> item.itemIndex() != null && !"APPROVED".equals(item.status()) && !"NOT_RECOMMENDED".equals(item.status()) && !"SUPPLEMENTED".equals(item.status()))
                .map(item -> "Case " + (item.itemIndex() + 1) + ": " + item.summary())
                .toList();
        String summary = "AI review completed for " + updates.size() + " generated cases.";
        return new AiReviewResult(result, summary, issues, suggestions, updates.values().stream()
                .filter(item -> !"SUPPLEMENTED".equals(item.status()))
                .map(item -> new AiReviewCaseDecision(
                        item.itemIndex(),
                        item.status(),
                        item.summary(),
                        item.coverageComment(),
                        item.evidenceComment(),
                        item.reviewComment(),
                        item.optimizationReason(),
                        item.coverageGap(),
                        item.optimizedCase()
                ))
                .toList(), updates.values().stream()
                .filter(item -> "SUPPLEMENTED".equals(item.status()) && item.supplementCase() != null)
                .map(item -> withSupplementReviewMetadata(item.supplementCase(), item.summary(), item.supplementReason(), item.coverageGap()))
                .toList(), List.of(), rawContent, true);
    }

    private void emitCompleteReviewResultAsUpdates(
            AiReviewResult reviewResult,
            String rawContent,
            int caseCount,
            Map<Integer, ReviewCaseStreamUpdate> updates,
            Consumer<ReviewCaseStreamUpdate> reviewConsumer
    ) {
        if (reviewResult.caseDecisions() != null && !reviewResult.caseDecisions().isEmpty()) {
            for (AiReviewCaseDecision decision : reviewResult.caseDecisions()) {
                if (decision.caseIndex() == null || decision.caseIndex() < 0 || decision.caseIndex() >= caseCount) {
                    continue;
                }
                ReviewCaseStreamUpdate update = new ReviewCaseStreamUpdate(
                        decision.caseIndex(),
                        decision.status() == null ? "CONFIRM_REQUIRED" : decision.status(),
                        decision.summary(),
                        decision.coverageComment(),
                        decision.evidenceComment(),
                        decision.reviewComment(),
                        decision.optimizationReason(),
                        null,
                        decision.coverageGap(),
                        decision.optimizedCase(),
                        null,
                        rawContent
                );
                updates.put(decision.caseIndex(), update);
                if (reviewConsumer != null) {
                    reviewConsumer.accept(update);
                }
            }
        }
        if (reviewResult.supplementCases() != null && !reviewResult.supplementCases().isEmpty()) {
            for (GeneratedAiCaseItem item : reviewResult.supplementCases()) {
                ReviewCaseStreamUpdate update = new ReviewCaseStreamUpdate(
                        null,
                        "SUPPLEMENTED",
                        firstNonBlank(item.aiReviewSummary(), item.supplementReason(), item.coverageGap()),
                        null,
                        null,
                        item.reviewComment(),
                        null,
                        item.supplementReason(),
                        item.coverageGap(),
                        null,
                        item,
                        rawContent
                );
                updates.put(-(updates.size() + 1), update);
                if (reviewConsumer != null) {
                    reviewConsumer.accept(update);
                }
            }
        }
        if (updates.isEmpty()) {
            for (int index = 0; index < caseCount; index += 1) {
                ReviewCaseStreamUpdate update = new ReviewCaseStreamUpdate(
                        index,
                        "CONFIRM_REQUIRED",
                        reviewResult.summary(),
                        reviewResult.summary(),
                        "完整输出评审未返回逐条依据评价，请查看评审原始输出。",
                        reviewResult.summary(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        rawContent
                );
                updates.put(index, update);
                if (reviewConsumer != null) {
                    reviewConsumer.accept(update);
                }
            }
        }
    }

    private String generationCoverageSummary(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(rawContent);
            JsonNode node = parsed.path("coverageSummary");
            return node.isTextual() && !node.asText().trim().isBlank() ? node.asText().trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> generationRemainingCoverageGaps(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(rawContent);
            JsonNode node = parsed.path("remainingCoverageGaps");
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().trim().isBlank()) {
                    values.add(item.asText().trim());
                }
            }
            return values;
        } catch (Exception ignored) {
            return List.of();
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

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "*".repeat(apiKey.length());
        }
        return apiKey.substring(0, 4) + "*".repeat(apiKey.length() - 8) + apiKey.substring(apiKey.length() - 4);
    }

    public record AiStreamModelInfo(
            String provider,
            String model
    ) {
    }

    public record GeneratedCaseStreamUpdate(
            Integer itemIndex,
            GeneratedAiCaseItem item,
            String rawOutput
    ) {
    }

    public record ReviewCaseStreamUpdate(
            Integer itemIndex,
            String status,
            String summary,
            String coverageComment,
            String evidenceComment,
            String reviewComment,
            String optimizationReason,
            String supplementReason,
            String coverageGap,
            GeneratedAiCaseItem optimizedCase,
            GeneratedAiCaseItem supplementCase,
            String rawOutput
    ) {
    }

    public record StreamedGenerateCasesResult(
            String workspaceCode,
            String workspaceName,
            String provider,
            String model,
            Integer systemMaxCases,
            Integer requestedMaxCases,
            Integer effectiveMaxCases,
            Integer actualGeneratedCount,
            List<GeneratedAiCaseItem> generatedCases,
            String coverageSummary,
            List<String> remainingCoverageGaps,
            List<String> warnings,
            List<AiInvalidCaseItem> invalidCases,
            String rawContent,
            boolean fallbackToComplete,
            String fallbackReason,
            boolean ignoredImages
    ) {
    }

    public record StreamedReviewResult(
            String provider,
            String model,
            AiReviewResult reviewResult,
            String rawContent,
            boolean fallbackToComplete,
            String fallbackReason
    ) {
    }

    private record ResolvedRoleConfig(
            AiCaseConfigEntity roleConfig,
            AiProviderConnectionEntity connection,
            AiProviderRequestProfile profile,
            String apiKey,
            AiModelCapabilities detectedCapabilities,
            AiModelCapabilities effectiveCapabilities
    ) {
        private AiProviderRequestProfile profileWithMaxCases(Integer maxCases) {
            return new AiProviderRequestProfile(
                    profile.protocolType(),
                    profile.provider(),
                    profile.model(),
                    profile.baseUrl(),
                    profile.temperature(),
                    maxCases,
                    profile.requestTimeoutSeconds()
            );
        }
    }
}
