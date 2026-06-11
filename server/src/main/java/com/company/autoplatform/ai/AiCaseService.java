package com.company.autoplatform.ai;

import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.casecenter.CaseDetailResponse;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import com.company.autoplatform.ai.AiCaseConfigDomainService.ResolvedRoleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AiCaseService {

    private static final String ROLE_GENERATOR = AiCaseConfigDomainService.ROLE_GENERATOR;
    private static final String ROLE_REVIEWER = AiCaseConfigDomainService.ROLE_REVIEWER;
    public static final int INITIAL_SMART_MAX_CASES = 50;
    public static final int REVIEW_SUPPLEMENT_MAX_CASES = 30;
    public static final int FINAL_MAX_CASES = 80;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiCaseConfigDomainService aiCaseConfigDomainService;
    private final AiRequirementAssetDomainService aiRequirementAssetDomainService;
    private final AiPromptBuilderSupport aiPromptBuilderSupport;
    private final WorkspaceService workspaceService;
    private final AiProviderClient aiProviderClient;
    private final AiProviderDomainService aiProviderDomainService;

    public AiCaseService(
            AiCaseConfigDomainService aiCaseConfigDomainService,
            AiRequirementAssetDomainService aiRequirementAssetDomainService,
            AiPromptBuilderSupport aiPromptBuilderSupport,
            WorkspaceService workspaceService,
            AiProviderClient aiProviderClient,
            AiProviderDomainService aiProviderDomainService
    ) {
        this.aiCaseConfigDomainService = aiCaseConfigDomainService;
        this.aiRequirementAssetDomainService = aiRequirementAssetDomainService;
        this.aiPromptBuilderSupport = aiPromptBuilderSupport;
        this.workspaceService = workspaceService;
        this.aiProviderClient = aiProviderClient;
        this.aiProviderDomainService = aiProviderDomainService;
    }

    public AiCaseConfigResponse getConfig(String headerWorkspaceCode, String targetWorkspaceCode) {
        return aiCaseConfigDomainService.getConfig(headerWorkspaceCode, targetWorkspaceCode);
    }

    public AiCaseConfigItem createConfig(String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        return aiCaseConfigDomainService.createConfig(headerWorkspaceCode, request);
    }

    public AiCaseConfigItem updateConfig(Long id, String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        return aiCaseConfigDomainService.updateConfig(id, headerWorkspaceCode, request);
    }

    public TestAiCaseConfigResponse testConfig(String headerWorkspaceCode, SaveAiCaseConfigRequest request) {
        return aiCaseConfigDomainService.testConfig(headerWorkspaceCode, request);
    }

    public AiCaseConfigSecretResponse getConfigSecret(Long id, String headerWorkspaceCode) {
        return aiCaseConfigDomainService.getConfigSecret(id, headerWorkspaceCode);
    }

    public AiProviderConnectionSecretResponse getProviderSecret(Long id, String headerWorkspaceCode) {
        return aiProviderDomainService.getProviderSecret(id, headerWorkspaceCode);
    }

    public void validateGenerationImageSupport(List<Long> assetIds) {
        List<AiRequirementAssetEntity> assets = aiRequirementAssetDomainService.loadRequirementAssets(assetIds);
        if (assets.isEmpty()) {
            return;
        }
        ResolvedRoleConfig resolved = aiCaseConfigDomainService.requireResolvedRoleConfig(ROLE_GENERATOR);
        if (!aiCaseConfigDomainService.supportsImageInputForGeneration(resolved)) {
            throw new BadRequestException("当前生成模型不支持图片识别，是否忽略图片并仅基于文本继续生成？");
        }
    }

    public GenerateAiCasesResponse generateCases(String headerWorkspaceCode, GenerateAiCasesRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        ResolvedRoleConfig resolved = aiCaseConfigDomainService.requireResolvedRoleConfig(ROLE_GENERATOR);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (aiCaseConfigDomainService.normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active personal case generator config found");
        }
        int systemMaxCases = INITIAL_SMART_MAX_CASES;
        int requestedMaxCases = request.maxCases() == null ? INITIAL_SMART_MAX_CASES : request.maxCases();
        int effectiveMaxCases = Math.min(requestedMaxCases, INITIAL_SMART_MAX_CASES);
        List<AiRequirementAssetEntity> assets = aiRequirementAssetDomainService.loadRequirementAssets(request.assetIds());
        if (!assets.isEmpty() && !aiCaseConfigDomainService.supportsImageInputForGeneration(resolved)) {
            throw new BadRequestException("The current AI config does not support image input. Remove the images or enable an image-capable model.");
        }
        boolean ignoredImages = false;
        String prompt = aiPromptBuilderSupport.buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, assets, false);
        AiGeneratedCasesResult result;
        try {
            result = aiProviderClient.generate(
                    resolved.profileWithMaxCases(effectiveMaxCases),
                    resolved.apiKey(),
                    prompt,
                    aiRequirementAssetDomainService.toImageInputs(assets)
            );
        } catch (BadRequestException exception) {
            if (assets.isEmpty() || !isImageInputUnsupportedError(exception)) {
                throw exception;
            }
            ignoredImages = true;
            prompt = aiPromptBuilderSupport.buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, List.of(), false);
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
        ResolvedRoleConfig resolved = aiCaseConfigDomainService.requireResolvedRoleConfig(ROLE_GENERATOR);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (aiCaseConfigDomainService.normalizeStatus(config.getStatus()) != 1) {
            throw new BadRequestException("No active personal case generator config found");
        }
        int systemMaxCases = INITIAL_SMART_MAX_CASES;
        int requestedMaxCases = request.maxCases() == null ? INITIAL_SMART_MAX_CASES : request.maxCases();
        int effectiveMaxCases = Math.min(requestedMaxCases, INITIAL_SMART_MAX_CASES);
        List<AiRequirementAssetEntity> assets = aiRequirementAssetDomainService.loadRequirementAssets(request.assetIds());
        if (!assets.isEmpty() && !aiCaseConfigDomainService.supportsImageInputForGeneration(resolved)) {
            throw new BadRequestException("The current AI config does not support image input. Remove the images or enable an image-capable model.");
        }
        if (modelConsumer != null) {
            modelConsumer.accept(new AiStreamModelInfo(resolved.profile().provider(), config.getModel()));
        }
        boolean ignoredImages = false;
        String prompt = aiPromptBuilderSupport.buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, assets, true);
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
            prompt = aiPromptBuilderSupport.buildGeneratorPrompt(config, request, workspace, effectiveMaxCases, List.of(), true);
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
        return aiProviderDomainService.getProviders(headerWorkspaceCode);
    }

    public AiProviderConnectionItem createProvider(String headerWorkspaceCode, SaveAiProviderConnectionRequest request) {
        return aiProviderDomainService.createProvider(headerWorkspaceCode, request);
    }

    public AiProviderConnectionItem updateProvider(Long id, String headerWorkspaceCode, SaveAiProviderConnectionRequest request) {
        return aiProviderDomainService.updateProvider(id, headerWorkspaceCode, request);
    }

    public PreviewAiProviderModelsResponse previewProviderModels(String headerWorkspaceCode, PreviewAiProviderModelsRequest request) {
        return aiProviderDomainService.previewProviderModels(headerWorkspaceCode, request);
    }

    @Transactional
    public void deleteProvider(Long id, String headerWorkspaceCode) {
        aiProviderDomainService.deleteProvider(id, headerWorkspaceCode);
    }

    public TestAiProviderConnectionResponse testProvider(Long id, String headerWorkspaceCode) {
        return aiProviderDomainService.testProvider(id, headerWorkspaceCode);
    }

    public FetchAiProviderModelsResponse fetchProviderModels(Long id, String headerWorkspaceCode) {
        return aiProviderDomainService.fetchProviderModels(id, headerWorkspaceCode);
    }

    public List<AiProviderModelItem> getProviderModels(Long id, String headerWorkspaceCode) {
        return aiProviderDomainService.getProviderModels(id, headerWorkspaceCode);
    }

    public AiProviderModelItem probeProviderModel(Long id, String headerWorkspaceCode, ProbeAiProviderModelRequest request) {
        return aiProviderDomainService.probeProviderModel(id, headerWorkspaceCode, request);
    }

    public AiCaseConfigResponse bootstrapConfigFromLegacy(String headerWorkspaceCode) {
        return aiCaseConfigDomainService.bootstrapConfigFromLegacy(headerWorkspaceCode);
    }

    public ImportRequirementDocumentResponse importRequirementDocument(String headerWorkspaceCode, MultipartFile file) {
        return aiRequirementAssetDomainService.importRequirementDocument(headerWorkspaceCode, file);
    }

    public List<AiRequirementAssetResponse> uploadRequirementAssets(String headerWorkspaceCode, List<MultipartFile> files) {
        return aiRequirementAssetDomainService.uploadRequirementAssets(headerWorkspaceCode, files);
    }

    public void deleteRequirementAsset(Long id, String headerWorkspaceCode) {
        aiRequirementAssetDomainService.deleteRequirementAsset(id, headerWorkspaceCode);
    }

    public AiRequirementAssetDownload downloadRequirementAsset(Long id, String headerWorkspaceCode) {
        return aiRequirementAssetDomainService.downloadRequirementAsset(id, headerWorkspaceCode);
    }

    public AiReviewResult reviewGeneratedCases(String headerWorkspaceCode, ReviewAiGeneratedCasesRequest request) {
        ResolvedRoleConfig resolved = aiCaseConfigDomainService.requireResolvedRoleConfig(ROLE_REVIEWER);
        AiCaseConfigEntity config = resolved.roleConfig();
        String prompt = aiPromptBuilderSupport.buildGeneratedCasesReviewPrompt(config, request, false);
        return aiProviderClient.review(resolved.profile(), resolved.apiKey(), prompt);
    }

    public StreamedReviewResult streamReviewGeneratedCases(
            String headerWorkspaceCode,
            ReviewAiGeneratedCasesRequest request,
            Consumer<AiStreamModelInfo> modelConsumer,
            Consumer<ReviewCaseStreamUpdate> reviewConsumer
    ) {
        ResolvedRoleConfig resolved = aiCaseConfigDomainService.requireResolvedRoleConfig(ROLE_REVIEWER);
        AiCaseConfigEntity config = resolved.roleConfig();
        if (modelConsumer != null) {
            modelConsumer.accept(new AiStreamModelInfo(resolved.profile().provider(), config.getModel()));
        }
        String prompt = aiPromptBuilderSupport.buildGeneratedCasesReviewPrompt(config, request, true);
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
        ResolvedRoleConfig resolved = aiCaseConfigDomainService.requireResolvedRoleConfig(ROLE_REVIEWER);
        AiCaseConfigEntity config = resolved.roleConfig();
        String prompt = aiPromptBuilderSupport.buildSavedCaseReviewPrompt(config, detail);
        return aiProviderClient.review(resolved.profile(), resolved.apiKey(), prompt);
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
}
