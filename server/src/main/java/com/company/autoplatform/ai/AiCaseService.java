package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.casecenter.CaseDetailResponse;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
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
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    public GenerateAiCasesResponse generateCases(String headerWorkspaceCode, GenerateAiCasesRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        ResolvedRoleConfig resolved = requireResolvedRoleConfig(ROLE_GENERATOR);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active personal case generator config found");
        }
        int systemMaxCases = config.getMaxCases();
        int requestedMaxCases = request.maxCases() == null ? systemMaxCases : request.maxCases();
        int effectiveMaxCases = Math.min(requestedMaxCases, systemMaxCases);
        List<AiRequirementAssetEntity> assets = loadRequirementAssets(request.assetIds());
        if (!assets.isEmpty() && !resolved.effectiveCapabilities().supportsImageInput()) {
            throw new BadRequestException("The current AI config does not support image input. Remove the images or enable an image-capable model.");
        }
        String prompt = buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, assets);
        AiGeneratedCasesResult result = aiProviderClient.generate(
                resolved.profileWithMaxCases(effectiveMaxCases),
                resolved.apiKey(),
                prompt,
                toImageInputs(assets)
        );
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
                result.warnings(),
                result.invalidCases(),
                result.rawContent()
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

    public void deleteProvider(Long id, String headerWorkspaceCode) {
        requireProviderConnection(id);
        Long bindingCount = aiCaseConfigMapper.selectCount(new LambdaQueryWrapper<AiCaseConfigEntity>()
                .eq(AiCaseConfigEntity::getOwnerUserId, CurrentUserContext.get())
                .eq(AiCaseConfigEntity::getProviderConnectionId, id));
        if (bindingCount != null && bindingCount > 0) {
            throw new BadRequestException("当前连接已被用例生成或评审角色绑定，不能删除");
        }
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
        String prompt = buildGeneratedCasesReviewPrompt(config, request);
        return aiProviderClient.review(resolved.profile(), resolved.apiKey(), prompt);
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
        entity.setMaxCases(request.maxCases());
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
        entity.setWorkspaceId(PERSONAL_SCOPE_WORKSPACE_ID);
        entity.setOwnerUserId(CurrentUserContext.get());
        entity.setConnectionName(request.connectionName().trim());
        entity.setProtocolType(normalizeProtocolType(request.protocolType(), null, request.baseUrl()));
        entity.setBaseUrl(request.baseUrl().trim());
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
        String protocolType = normalizeProtocolType(connection.getProtocolType(), null, connection.getBaseUrl());
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
        String protocolType = normalizeProtocolType(request.protocolType(), null, request.baseUrl());
        return new AiProviderRequestProfile(
                protocolType,
                providerForProtocolType(protocolType),
                "model-probe",
                request.baseUrl().trim(),
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
                ? (existing == null ? 20 : existing.getMaxCases())
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

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "*".repeat(apiKey.length());
        }
        return apiKey.substring(0, 4) + "*".repeat(apiKey.length() - 8) + apiKey.substring(apiKey.length() - 4);
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
