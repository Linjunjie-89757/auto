package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.ai.AiProviderClient;
import com.company.autoplatform.ai.AiProviderConnectionEntity;
import com.company.autoplatform.ai.AiProviderConnectionMapper;
import com.company.autoplatform.ai.AiProviderRequestProfile;
import com.company.autoplatform.ai.AiSecretCodec;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Service
public class ApiAiCaseGenerationService {

    private static final int MAX_GENERATED_CASES = 80;
    private static final Logger log = LoggerFactory.getLogger(ApiAiCaseGenerationService.class);

    private final AiProviderConnectionMapper aiProviderConnectionMapper;
    private final WorkspaceService workspaceService;
    private final AiSecretCodec aiSecretCodec;
    private final AiProviderClient aiProviderClient;
    private final ObjectMapper objectMapper;
    private final int defaultRequestTimeoutSeconds;

    public ApiAiCaseGenerationService(
            AiProviderConnectionMapper aiProviderConnectionMapper,
            WorkspaceService workspaceService,
            AiSecretCodec aiSecretCodec,
            AiProviderClient aiProviderClient,
            ObjectMapper objectMapper,
            @Value("${app.ai.request-timeout-seconds:60}") int defaultRequestTimeoutSeconds
    ) {
        this.aiProviderConnectionMapper = aiProviderConnectionMapper;
        this.workspaceService = workspaceService;
        this.aiSecretCodec = aiSecretCodec;
        this.aiProviderClient = aiProviderClient;
        this.objectMapper = objectMapper;
        this.defaultRequestTimeoutSeconds = Math.max(10, Math.min(600, defaultRequestTimeoutSeconds));
    }

    public void streamGenerate(String headerWorkspaceCode, ApiAiCaseGenerationRequest request, Writer writer) throws IOException {
        try {
            WorkspaceEntity workspace = resolveWorkspace(headerWorkspaceCode, request.workspaceCode());
            ResolvedAiProvider provider = resolveProvider(request.providerConnectionId(), request.modelName());
            List<ApiAiCaseGenerationOption> options = normalizeOptions(request.options());
            int targetCount = resolveTargetCount(request.caseCount(), options.size());
            List<ApiAiCaseGenerationSlot> slots = buildSlots(options, targetCount);
            writeEvent(writer, new ApiAiCaseGenerationEvent("started", null, null, null, slots.size(), null, null, null));
            int completed = 0;
            try {
                Map<String, ApiAiGeneratedCaseOutline> outlines;
                try {
                    outlines = streamGenerateOutlines(workspace, provider, request, slots, writer);
                } catch (Exception exception) {
                    log.warn("API AI case outline generation failed, definitionId={}, definitionName={}, model={}, targetCount={}",
                            request.definitionId(), request.definitionName(), provider.profile().model(), slots.size(), exception);
                    String message = stageFailureMessage("\u5927\u7eb2\u751f\u6210\u9636\u6bb5\u5931\u8d25", exception);
                    for (ApiAiCaseGenerationSlot slot : slots) {
                        writeEvent(writer, new ApiAiCaseGenerationEvent("item_failed", slot.id(), slot.group(), slot.type(), slots.size(), null, null, message));
                    }
                    writeEvent(writer, new ApiAiCaseGenerationEvent("completed", null, null, null, slots.size(), null, null, "\u5927\u7eb2\u751f\u6210\u5931\u8d25"));
                    return;
                }
                completed = generateCasesFromOutlines(workspace, provider, request, slots, outlines, writer);
            } catch (Exception exception) {
                log.warn("API AI case generation failed, definitionId={}, definitionName={}, model={}, targetCount={}",
                        request.definitionId(), request.definitionName(), provider.profile().model(), slots.size(), exception);
                for (ApiAiCaseGenerationSlot slot : slots) {
                    writeEvent(writer, new ApiAiCaseGenerationEvent("item_failed", slot.id(), slot.group(), slot.type(), slots.size(), null, null,
                            stageFailureMessage("\u7528\u4f8b\u751f\u6210\u9636\u6bb5\u5931\u8d25", exception)));
                }
            }
            writeEvent(writer, new ApiAiCaseGenerationEvent("completed", null, null, null, slots.size(), null, null,
                    completed == slots.size() ? null : "\u90e8\u5206\u7528\u4f8b\u751f\u6210\u5931\u8d25"));
        } catch (Exception exception) {
            writeEvent(writer, new ApiAiCaseGenerationEvent("failed", null, null, null, null, null, null, exception.getMessage()));
        }
    }

    private Map<String, ApiAiGeneratedCaseOutline> streamGenerateOutlines(
            WorkspaceEntity workspace,
            ResolvedAiProvider provider,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots,
            Writer writer
    ) {
        String prompt = buildOutlinePrompt(workspace, request, slots);
        Map<String, ApiAiCaseGenerationSlot> slotById = new LinkedHashMap<>();
        for (ApiAiCaseGenerationSlot slot : slots) {
            slotById.put(slot.id(), slot);
        }
        Map<String, ApiAiGeneratedCaseOutline> outlines = new LinkedHashMap<>();
        StringBuilder lineBuffer = new StringBuilder();
        String content = aiProviderClient.streamStructuredContent(provider.profile(), provider.apiKey(), prompt, delta -> {
            appendAndEmitOutlineLines(delta, lineBuffer, slotById, outlines, writer);
        });
        appendAndEmitOutlineLines("\n", lineBuffer, slotById, outlines, writer);
        emitRemainingOutlines(content, slots, outlines, writer);
        return outlines;
    }

    private int generateCasesFromOutlines(
            WorkspaceEntity workspace,
            ResolvedAiProvider provider,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots,
            Map<String, ApiAiGeneratedCaseOutline> outlines,
            Writer writer
    ) throws IOException {
        int completed = 0;
        for (int index = 0; index < slots.size(); index++) {
            ApiAiCaseGenerationSlot slot = slots.get(index);
            ApiAiGeneratedCaseOutline outline = outlines.get(slot.id());
            if (outline == null) {
                writeEvent(writer, new ApiAiCaseGenerationEvent("item_failed", slot.id(), slot.group(), slot.type(), slots.size(), null, null,
                        "AI \u672a\u8fd4\u56de\u8be5\u7528\u4f8b\u5927\u7eb2"));
                continue;
            }
            try {
                ApiAiGeneratedCaseDraft draft = generateOneCaseFromOutline(workspace, provider, request, slot, outline, index + 1, slots.size());
                writeEvent(writer, new ApiAiCaseGenerationEvent("item_completed", slot.id(), slot.group(), slot.type(), slots.size(), draft, outline, null));
                completed += 1;
            } catch (RuntimeException exception) {
                log.warn("API AI case detail generation failed, definitionId={}, itemId={}, itemIndex={}, total={}, type={}, model={}",
                        request.definitionId(), slot.id(), index + 1, slots.size(), slot.type(), provider.profile().model(), exception);
                writeEvent(writer, new ApiAiCaseGenerationEvent("item_failed", slot.id(), slot.group(), slot.type(), slots.size(), null, outline,
                        "\u7b2c " + (index + 1) + "/" + slots.size() + " \u6761\u8be6\u60c5\u751f\u6210\u5931\u8d25\uff08" + slot.type() + "\uff09\uff1a"
                                + exceptionMessage(exception)));
            }
        }
        return completed;
    }

    private int streamGenerateBatchCases(
            WorkspaceEntity workspace,
            ResolvedAiProvider provider,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots,
            Writer writer
    ) {
        String prompt = buildStreamingBatchPrompt(workspace, request, slots);
        Map<String, ApiAiCaseGenerationSlot> slotById = new LinkedHashMap<>();
        for (ApiAiCaseGenerationSlot slot : slots) {
            slotById.put(slot.id(), slot);
        }
        Set<String> completedIds = new HashSet<>();
        StringBuilder lineBuffer = new StringBuilder();
        String content = aiProviderClient.streamStructuredContent(provider.profile(), provider.apiKey(), prompt, delta -> {
            appendAndEmitCompletedLines(delta, lineBuffer, slotById, completedIds, request, writer);
        });
        appendAndEmitCompletedLines("\n", lineBuffer, slotById, completedIds, request, writer);
        emitRemainingDrafts(content, slots, completedIds, request, writer);
        for (ApiAiCaseGenerationSlot slot : slots) {
            if (!completedIds.contains(slot.id())) {
                writeUnchecked(writer, new ApiAiCaseGenerationEvent("item_failed", slot.id(), slot.group(), slot.type(), slots.size(), null, null,
                        "AI \u672a\u8fd4\u56de\u8be5\u7528\u4f8b"));
            }
        }
        return completedIds.size();
    }

    private void appendAndEmitOutlineLines(
            String delta,
            StringBuilder lineBuffer,
            Map<String, ApiAiCaseGenerationSlot> slotById,
            Map<String, ApiAiGeneratedCaseOutline> outlines,
            Writer writer
    ) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        lineBuffer.append(delta);
        int lineBreakIndex;
        while ((lineBreakIndex = indexOfLineBreak(lineBuffer)) >= 0) {
            String line = lineBuffer.substring(0, lineBreakIndex).trim();
            int removeEnd = lineBreakIndex + 1;
            if (removeEnd < lineBuffer.length() && lineBuffer.charAt(lineBreakIndex) == '\r' && lineBuffer.charAt(removeEnd) == '\n') {
                removeEnd += 1;
            }
            lineBuffer.delete(0, removeEnd);
            emitOutlineLine(line, slotById, outlines, writer);
        }
    }

    private void appendAndEmitCompletedLines(
            String delta,
            StringBuilder lineBuffer,
            Map<String, ApiAiCaseGenerationSlot> slotById,
            Set<String> completedIds,
            ApiAiCaseGenerationRequest request,
            Writer writer
    ) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        lineBuffer.append(delta);
        int lineBreakIndex;
        while ((lineBreakIndex = indexOfLineBreak(lineBuffer)) >= 0) {
            String line = lineBuffer.substring(0, lineBreakIndex).trim();
            int removeEnd = lineBreakIndex + 1;
            if (removeEnd < lineBuffer.length() && lineBuffer.charAt(lineBreakIndex) == '\r' && lineBuffer.charAt(removeEnd) == '\n') {
                removeEnd += 1;
            }
            lineBuffer.delete(0, removeEnd);
            emitDraftLine(line, slotById, completedIds, request, writer);
        }
    }

    private int indexOfLineBreak(StringBuilder builder) {
        for (int index = 0; index < builder.length(); index++) {
            char value = builder.charAt(index);
            if (value == '\n' || value == '\r') {
                return index;
            }
        }
        return -1;
    }

    private void emitDraftLine(
            String line,
            Map<String, ApiAiCaseGenerationSlot> slotById,
            Set<String> completedIds,
            ApiAiCaseGenerationRequest request,
            Writer writer
    ) {
        if (line == null || line.isBlank() || line.startsWith("```")) {
            return;
        }
        try {
            ApiAiGeneratedCaseLine parsed = parseGeneratedCaseLine(line);
            ApiAiCaseGenerationSlot slot = slotById.get(parsed.id());
            if (slot == null || completedIds.contains(slot.id()) || parsed.draft() == null) {
                return;
            }
            ApiAiGeneratedCaseDraft normalized = normalizeDraft(parsed.draft(), request, slot, completedIds.size() + 1);
            completedIds.add(slot.id());
            writeUnchecked(writer, new ApiAiCaseGenerationEvent("item_completed", slot.id(), slot.group(), slot.type(), slotById.size(), normalized, null, null));
        } catch (RuntimeException exception) {
            // Ignore partial or non-NDJSON lines here. Final full-content parsing below is the fallback.
        }
    }

    private void emitOutlineLine(
            String line,
            Map<String, ApiAiCaseGenerationSlot> slotById,
            Map<String, ApiAiGeneratedCaseOutline> outlines,
            Writer writer
    ) {
        if (line == null || line.isBlank() || line.startsWith("```")) {
            return;
        }
        try {
            ApiAiGeneratedCaseOutlineLine parsed = parseGeneratedCaseOutlineLine(line);
            ApiAiCaseGenerationSlot slot = slotById.get(parsed.id());
            if (slot == null || outlines.containsKey(slot.id()) || parsed.outline() == null) {
                return;
            }
            ApiAiGeneratedCaseOutline normalized = normalizeOutline(parsed.outline(), slot);
            outlines.put(slot.id(), normalized);
            writeUnchecked(writer, new ApiAiCaseGenerationEvent("item_outline", slot.id(), slot.group(), slot.type(), slotById.size(), null, normalized, null));
        } catch (RuntimeException exception) {
            // Ignore partial or non-NDJSON lines. Full-content parsing below is the fallback.
        }
    }

    private ApiAiGeneratedCaseLine parseGeneratedCaseLine(String line) {
        try {
            JsonNode parsed = objectMapper.readTree(line);
            String id = optionalText(parsed, "id");
            JsonNode draftNode = parsed.has("case") ? parsed.path("case") : parsed;
            ApiAiGeneratedCaseDraft draft = objectMapper.treeToValue(draftNode, ApiAiGeneratedCaseDraft.class);
            return new ApiAiGeneratedCaseLine(id, draft);
        } catch (IOException exception) {
            throw new BadRequestException("AI \u8fd4\u56de\u7684\u5355\u6761\u7528\u4f8b\u65e0\u6cd5\u89e3\u6790");
        }
    }

    private ApiAiGeneratedCaseOutlineLine parseGeneratedCaseOutlineLine(String line) {
        try {
            JsonNode parsed = objectMapper.readTree(line);
            String id = optionalText(parsed, "id");
            JsonNode outlineNode = parsed.has("outline") ? parsed.path("outline") : parsed;
            ApiAiGeneratedCaseOutline outline = objectMapper.treeToValue(outlineNode, ApiAiGeneratedCaseOutline.class);
            return new ApiAiGeneratedCaseOutlineLine(id, outline);
        } catch (IOException exception) {
            throw new BadRequestException("AI \u8fd4\u56de\u7684\u5355\u6761\u7528\u4f8b\u5927\u7eb2\u65e0\u6cd5\u89e3\u6790");
        }
    }

    private String optionalText(JsonNode item, String field) {
        JsonNode fieldNode = item == null ? null : item.path(field);
        if (fieldNode == null || fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        String value = fieldNode.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void emitRemainingDrafts(
            String content,
            List<ApiAiCaseGenerationSlot> slots,
            Set<String> completedIds,
            ApiAiCaseGenerationRequest request,
            Writer writer
    ) {
        if (content == null || content.isBlank() || completedIds.size() >= slots.size()) {
            return;
        }
        List<ApiAiGeneratedCaseDraft> drafts;
        try {
            drafts = parseDrafts(content);
        } catch (RuntimeException exception) {
            return;
        }
        for (int index = 0; index < slots.size(); index++) {
            ApiAiCaseGenerationSlot slot = slots.get(index);
            if (completedIds.contains(slot.id())) {
                continue;
            }
            ApiAiGeneratedCaseDraft draft = index < drafts.size() ? drafts.get(index) : null;
            if (draft == null) {
                continue;
            }
            ApiAiGeneratedCaseDraft normalized = normalizeDraft(draft, request, slot, index + 1);
            completedIds.add(slot.id());
            writeUnchecked(writer, new ApiAiCaseGenerationEvent("item_completed", slot.id(), slot.group(), slot.type(), slots.size(), normalized, null, null));
        }
    }

    private void emitRemainingOutlines(
            String content,
            List<ApiAiCaseGenerationSlot> slots,
            Map<String, ApiAiGeneratedCaseOutline> outlines,
            Writer writer
    ) {
        if (content == null || content.isBlank() || outlines.size() >= slots.size()) {
            return;
        }
        List<ApiAiGeneratedCaseOutlineLine> parsedLines = parseOutlinesFromNdjson(content);
        Map<String, ApiAiCaseGenerationSlot> slotById = new LinkedHashMap<>();
        for (ApiAiCaseGenerationSlot slot : slots) {
            slotById.put(slot.id(), slot);
        }
        for (ApiAiGeneratedCaseOutlineLine parsed : parsedLines) {
            ApiAiCaseGenerationSlot slot = slotById.get(parsed.id());
            if (slot == null || outlines.containsKey(slot.id()) || parsed.outline() == null) {
                continue;
            }
            ApiAiGeneratedCaseOutline normalized = normalizeOutline(parsed.outline(), slot);
            outlines.put(slot.id(), normalized);
            writeUnchecked(writer, new ApiAiCaseGenerationEvent("item_outline", slot.id(), slot.group(), slot.type(), slots.size(), null, normalized, null));
        }
    }

    private List<ApiAiGeneratedCaseOutlineLine> parseOutlinesFromNdjson(String content) {
        List<ApiAiGeneratedCaseOutlineLine> outlines = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return outlines;
        }
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("```")) {
                continue;
            }
            try {
                outlines.add(parseGeneratedCaseOutlineLine(line));
            } catch (RuntimeException exception) {
                // Keep parsing later lines; one malformed line should not discard a whole batch.
            }
        }
        return outlines;
    }

    private void writeUnchecked(Writer writer, ApiAiCaseGenerationEvent event) {
        try {
            writeEvent(writer, event);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private ApiAiGeneratedCaseDraft generateOneCase(
            WorkspaceEntity workspace,
            ResolvedAiProvider provider,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            int index,
            int total
    ) {
        String prompt = buildPrompt(workspace, request, slot, index, total);
        String content = aiProviderClient.requestStructuredContent(provider.profile(), provider.apiKey(), prompt);
        return normalizeDraft(parseDraft(content), request, slot, index);
    }

    private ApiAiGeneratedCaseDraft generateOneCaseFromOutline(
            WorkspaceEntity workspace,
            ResolvedAiProvider provider,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            ApiAiGeneratedCaseOutline outline,
            int index,
            int total
    ) {
        String prompt = buildPrompt(workspace, request, slot, outline, index, total);
        String content = aiProviderClient.requestStructuredContent(provider.profile(), provider.apiKey(), prompt);
        return normalizeDraft(parseDraft(content), request, slot, index);
    }

    private List<ApiAiGeneratedCaseDraft> parseDrafts(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode casesNode = parsed.isArray() ? parsed : parsed.path("cases");
            if (!casesNode.isArray()) {
                List<ApiAiGeneratedCaseDraft> ndjsonDrafts = parseDraftsFromNdjson(content);
                if (!ndjsonDrafts.isEmpty()) {
                    return ndjsonDrafts;
                }
                throw new BadRequestException("AI \u8fd4\u56de\u5185\u5bb9\u65e0\u6cd5\u89e3\u6790\u4e3a\u63a5\u53e3\u7528\u4f8b JSON \u5217\u8868");
            }
            List<ApiAiGeneratedCaseDraft> drafts = new ArrayList<>();
            for (JsonNode itemNode : casesNode) {
                try {
                    drafts.add(objectMapper.treeToValue(itemNode, ApiAiGeneratedCaseDraft.class));
                } catch (IOException exception) {
                    drafts.add(null);
                }
            }
            return drafts;
        } catch (IOException exception) {
            List<ApiAiGeneratedCaseDraft> ndjsonDrafts = parseDraftsFromNdjson(content);
            if (!ndjsonDrafts.isEmpty()) {
                return ndjsonDrafts;
            }
            throw new BadRequestException("AI \u8fd4\u56de\u5185\u5bb9\u65e0\u6cd5\u89e3\u6790\u4e3a\u63a5\u53e3\u7528\u4f8b JSON \u5217\u8868");
        }
    }

    private List<ApiAiGeneratedCaseDraft> parseDraftsFromNdjson(String content) {
        List<ApiAiGeneratedCaseDraft> drafts = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return drafts;
        }
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("```")) {
                continue;
            }
            try {
                ApiAiGeneratedCaseLine parsed = parseGeneratedCaseLine(line);
                if (parsed.draft() != null) {
                    drafts.add(parsed.draft());
                }
            } catch (RuntimeException exception) {
                // Keep parsing later lines; one malformed line should not discard a whole batch.
            }
        }
        return drafts;
    }

    private ApiAiGeneratedCaseDraft parseDraft(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode draftNode = parsed.has("case") ? parsed.path("case") : parsed;
            ApiAiGeneratedCaseDraft draft = objectMapper.treeToValue(draftNode, ApiAiGeneratedCaseDraft.class);
            if (draft == null) {
                throw new BadRequestException("AI \u8fd4\u56de\u5185\u5bb9\u4e3a\u7a7a");
            }
            return draft;
        } catch (IOException exception) {
            throw new BadRequestException("AI \u8fd4\u56de\u5185\u5bb9\u65e0\u6cd5\u89e3\u6790\u4e3a\u63a5\u53e3\u7528\u4f8b JSON");
        }
    }

    private ApiAiGeneratedCaseDraft normalizeDraft(
            ApiAiGeneratedCaseDraft draft,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            int index
    ) {
        ApiRequestConfigInput sourceRequest = request.requestConfig();
        ApiRequestConfigInput generatedRequest = draft.requestConfig();
        String method = firstNonBlank(generatedRequest == null ? null : generatedRequest.method(),
                sourceRequest == null ? null : sourceRequest.method(), "GET").toUpperCase(Locale.ROOT);
        String path = firstNonBlank(generatedRequest == null ? null : generatedRequest.path(),
                sourceRequest == null ? null : sourceRequest.path(), request.path(), "/");
        ApiRequestConfigInput requestConfig = new ApiRequestConfigInput(
                method,
                path,
                generatedRequest != null && generatedRequest.timeoutMs() != null
                        ? generatedRequest.timeoutMs()
                        : (sourceRequest == null ? 10000 : Optional.ofNullable(sourceRequest.timeoutMs()).orElse(10000)),
                defaultList(generatedRequest == null ? null : generatedRequest.queryParams(),
                        sourceRequest == null ? null : sourceRequest.queryParams()),
                defaultList(generatedRequest == null ? null : generatedRequest.headers(),
                        sourceRequest == null ? null : sourceRequest.headers()),
                defaultList(generatedRequest == null ? null : generatedRequest.cookies(),
                        sourceRequest == null ? null : sourceRequest.cookies()),
                generatedRequest != null && generatedRequest.body() != null
                        ? generatedRequest.body()
                        : (sourceRequest == null || sourceRequest.body() == null
                        ? new ApiRequestBodyInput("NONE", null, List.of(), null, null, null)
                        : sourceRequest.body()),
                generatedRequest != null && generatedRequest.authConfig() != null
                        ? normalizeAuth(generatedRequest.authConfig())
                        : normalizeAuth(sourceRequest == null ? null : sourceRequest.authConfig())
        );
        List<ApiAssertionInput> assertions = defaultList(draft.assertions(), request.assertions());
        if (assertions.isEmpty()) {
            assertions = List.of(new ApiAssertionInput("STATUS_CODE", "STATUS_CODE", "LT", slot.groupKey().equals("positive") ? "400" : "500"));
        }
        String expected = firstNonBlank(draft.expected(), defaultExpected(slot));
        return new ApiAiGeneratedCaseDraft(
                normalizeCaseName(draft.name(), slot, method, path, expected),
                blankToNull(draft.description()) == null ? defaultDescription(slot) : draft.description().trim(),
                normalizeTags(draft.tags(), slot),
                slot.group(),
                slot.groupKey(),
                slot.type(),
                slot.typeKey(),
                expected,
                requestConfig,
                assertions,
                defaultList(draft.preProcessors(), request.preProcessors()),
                defaultList(draft.postProcessors(), request.postProcessors())
        );
    }

    private ApiAiGeneratedCaseOutline normalizeOutline(ApiAiGeneratedCaseOutline outline, ApiAiCaseGenerationSlot slot) {
        String expected = firstNonBlank(outline.expected(), defaultExpected(slot));
        String name = normalizeCaseName(firstNonBlank(outline.name(), slot.type() + " \u2013 " + expected), slot, "", "", expected);
        String description = blankToNull(outline.description()) == null ? defaultDescription(slot) : outline.description().trim();
        return new ApiAiGeneratedCaseOutline(
                name,
                description,
                normalizeTags(outline.tags(), slot),
                slot.group(),
                slot.groupKey(),
                slot.type(),
                slot.typeKey(),
                expected
        );
    }

    private String buildPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            int index,
            int total
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        payload.put("target", Map.of(
                "index", index,
                "total", total,
                "group", slot.group(),
                "groupKey", slot.groupKey(),
                "type", slot.type(),
                "typeKey", slot.typeKey(),
                "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
        ));
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\u548c\u76ee\u6807\u7c7b\u578b\uff0c\u53ea\u751f\u6210 1 \u6761\u9ad8\u8d28\u91cf\u63a5\u53e3\u7528\u4f8b\u3002
                \u53ea\u8fd4\u56de\u4e25\u683c JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\u3001\u89e3\u91ca\u6587\u5b57\u6216\u4ee3\u7801\u5757\u3002
                \u8fd4\u56de\u7ed3\u6784\u5fc5\u987b\u5339\u914d\u4e0b\u9762\u793a\u4f8b\uff1a
                {
                  "name": "\u7528\u4f8b\u7c7b\u578b \u2013 \u7528\u4f8b\u573a\u666f \u2013 \u671f\u671b\u7ed3\u679c",
                  "description": "\u7528\u4f8b\u8bf4\u660e",
                  "tags": ["\u6807\u7b7e"],
                  "expected": "\u9884\u671f\u7ed3\u679c",
                  "requestConfig": {
                    "method": "GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE",
                    "path": "\u63a5\u53e3\u8def\u5f84",
                    "timeoutMs": 10000,
                    "queryParams": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "headers": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "cookies": [],
                    "body": {"type":"NONE|FORM_DATA|X_WWW_FORM_URLENCODED|RAW_JSON|RAW_XML|RAW_TEXT|BINARY","rawText":"","formItems":[],"contentType":"","fileName":"","binaryBase64":""},
                    "authConfig": {"authType":"NONE|BASIC|DIGEST","basicAuth":{"userName":"","password":""},"digestAuth":{"userName":"","password":""}}
                  },
                  "assertions": [{"type":"STATUS_CODE","subject":"STATUS_CODE","operator":"LT","expectedValue":"400","enabled":true}],
                  "preProcessors": [],
                  "postProcessors": []
                }
                \u89c4\u5219\uff1a
                1. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                2. \u53c2\u6570\u5b57\u6bb5\u5c3d\u91cf\u8865\u5168 required\u3001paramType\u3001minLength\u3001maxLength\u3001description\u3001enabled\u3002
                3. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                4. assertions \u8981\u80fd\u9a8c\u8bc1 expected\uff0c\u6b63\u5411\u7528\u4f8b\u901a\u5e38\u65ad\u8a00 2xx/3xx\uff0c\u53cd\u5411\u6216\u5b89\u5168\u7528\u4f8b\u901a\u5e38\u65ad\u8a00\u5408\u7406\u9519\u8bef\u7801\u6216\u9519\u8bef\u4fe1\u606f\u3002
                5. name \u4e0d\u8981\u5305\u542b\u5206\u7ec4\u524d\u7f00\uff0c\u4f8b\u5982\u4e0d\u8981\u5199\u3010\u6b63\u5411\u3011\u3002\u63a8\u8350\u683c\u5f0f\uff1a\u7c7b\u578b \u2013 \u573a\u666f\u63cf\u8ff0 \u2013 \u671f\u671b\u8fd4\u56de\u7ed3\u679c\u3002
                6. expected \u7528\u4e00\u53e5\u8bdd\u63cf\u8ff0\u65ad\u8a00\u610f\u56fe\uff0c\u4e0d\u8981\u4e3a\u7a7a\u3002
                7. \u6240\u6709\u8f93\u51fa\u5fc5\u987b\u662f\u5408\u6cd5 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    private String buildPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            ApiAiGeneratedCaseOutline outline,
            int index,
            int total
    ) {
        Map<String, Object> payload = buildSingleCasePayload(workspace, request, slot, index, total);
        payload.put("outline", outline);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\u548c\u5df2\u786e\u5b9a\u7684\u7528\u4f8b\u5927\u7eb2\uff0c\u53ea\u751f\u6210 1 \u6761\u5b8c\u6574\u63a5\u53e3\u7528\u4f8b\u8be6\u60c5\u3002
                \u53ea\u8fd4\u56de\u4e25\u683c JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\u3001\u89e3\u91ca\u6587\u5b57\u6216\u4ee3\u7801\u5757\u3002
                \u8fd4\u56de\u7ed3\u6784\u5fc5\u987b\u5339\u914d\u4e0b\u9762\u793a\u4f8b\uff1a
                {
                  "name": "\u5927\u7eb2\u4e2d\u7684 name",
                  "description": "\u7528\u4f8b\u8bf4\u660e",
                  "tags": ["\u6807\u7b7e"],
                  "expected": "\u9884\u671f\u7ed3\u679c",
                  "requestConfig": {
                    "method": "GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE",
                    "path": "\u63a5\u53e3\u8def\u5f84",
                    "timeoutMs": 10000,
                    "queryParams": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "headers": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "cookies": [],
                    "body": {"type":"NONE|FORM_DATA|X_WWW_FORM_URLENCODED|RAW_JSON|RAW_XML|RAW_TEXT|BINARY","rawText":"","formItems":[],"contentType":"","fileName":"","binaryBase64":""},
                    "authConfig": {"authType":"NONE|BASIC|DIGEST","basicAuth":{"userName":"","password":""},"digestAuth":{"userName":"","password":""}}
                  },
                  "assertions": [{"type":"STATUS_CODE","subject":"STATUS_CODE","operator":"LT","expectedValue":"400","enabled":true}],
                  "preProcessors": [],
                  "postProcessors": []
                }
                \u89c4\u5219\uff1a
                1. name\u3001description\u3001tags\u3001expected \u8981\u4e0e outline \u4fdd\u6301\u4e00\u81f4\uff0c\u4e0d\u8981\u66f4\u6362\u6210\u5176\u4ed6\u573a\u666f\u3002
                2. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                3. requestConfig \u8981\u4f53\u73b0 outline.type \u5bf9\u5e94\u7684\u7528\u4f8b\u5dee\u5f02\uff0c\u4f8b\u5982\u5fc5\u586b\u7f3a\u5931\u3001\u8fb9\u754c\u503c\u3001\u5f02\u5e38\u503c\u6216\u5b89\u5168\u8f93\u5165\u3002
                4. assertions \u8981\u80fd\u9a8c\u8bc1 expected\uff0c\u6b63\u5411\u7528\u4f8b\u901a\u5e38\u65ad\u8a00 2xx/3xx\uff0c\u53cd\u5411\u6216\u5b89\u5168\u7528\u4f8b\u901a\u5e38\u65ad\u8a00\u5408\u7406\u9519\u8bef\u7801\u6216\u9519\u8bef\u4fe1\u606f\u3002
                5. \u6240\u6709\u8f93\u51fa\u5fc5\u987b\u662f\u5408\u6cd5 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    private Map<String, Object> buildSingleCasePayload(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            int index,
            int total
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        payload.put("target", Map.of(
                "index", index,
                "total", total,
                "group", slot.group(),
                "groupKey", slot.groupKey(),
                "type", slot.type(),
                "typeKey", slot.typeKey(),
                "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
        ));
        return payload;
    }

    private String buildOutlinePrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            ApiAiCaseGenerationSlot slot = slots.get(index);
            targets.add(Map.of(
                    "index", index + 1,
                    "id", slot.id(),
                    "group", slot.group(),
                    "groupKey", slot.groupKey(),
                    "type", slot.type(),
                    "typeKey", slot.typeKey(),
                    "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                    "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
            ));
        }
        payload.put("targets", targets);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u8bbe\u8ba1\u52a9\u624b\u3002\u8bf7\u5148\u4e3a targets \u4e2d\u7684\u6bcf\u4e2a\u76ee\u6807\u8bbe\u8ba1 1 \u6761\u7528\u4f8b\u5927\u7eb2\uff0c\u4e0d\u8981\u751f\u6210\u5b8c\u6574\u8bf7\u6c42\u914d\u7f6e\u3002
                \u5fc5\u987b\u4f7f\u7528 NDJSON \u8f93\u51fa\uff1a\u4e00\u884c\u4e00\u6761\u5b8c\u6574 JSON\uff0c\u6bcf\u884c\u5bf9\u5e94\u4e00\u4e2a target\u3002\u4e0d\u8981\u8f93\u51fa Markdown\u3001\u4ee3\u7801\u5757\u3001\u89e3\u91ca\u6587\u5b57\u6216 JSON \u6570\u7ec4\u3002
                \u6bcf\u4e00\u884c\u5fc5\u987b\u662f\u5408\u6cd5 JSON\uff0c\u7ed3\u6784\u5982\u4e0b\uff1a
                {"id":"target id","outline":{"name":"\u7c7b\u578b \u2013 \u540d\u79f0 \u2013 \u671f\u671b","description":"\u8bf4\u660e","group":"\u6b63\u5411","groupKey":"positive","type":"\u4ec5\u4f20\u5fc5\u8981\u5b57\u6bb5","typeKey":"required-only","expected":"\u671f\u671b\u8fd4\u56de200\u6210\u529f","tags":["\u4ec5\u4f20\u5fc5\u8981\u5b57\u6bb5"]}}
                \u89c4\u5219\uff1a
                1. \u6bcf\u4e2a target \u5fc5\u987b\u8f93\u51fa\u4e00\u884c\uff0c\u8f93\u51fa\u987a\u5e8f\u5c3d\u91cf\u548c targets \u4e00\u81f4\u3002
                2. \u6bcf\u884c id \u5fc5\u987b\u7b49\u4e8e\u5bf9\u5e94 target.id\u3002
                3. outline.name \u5fc5\u987b\u4f7f\u7528\u201c\u7c7b\u578b \u2013 \u540d\u79f0 \u2013 \u671f\u671b\u201d\u683c\u5f0f\uff0c\u7c7b\u578b\u4f7f\u7528 target.type\u3002
                4. outline.group/groupKey/type/typeKey \u5fc5\u987b\u4e0e\u5bf9\u5e94 target \u4fdd\u6301\u4e00\u81f4\u3002
                5. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                6. \u5927\u7eb2\u53ea\u63cf\u8ff0\u8981\u751f\u6210\u7684\u7528\u4f8b\u610f\u56fe\uff0c\u4e0d\u8981\u8f93\u51fa requestConfig\u3001assertions\u3001preProcessors\u3001postProcessors\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    private String buildBatchPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            ApiAiCaseGenerationSlot slot = slots.get(index);
            targets.add(Map.of(
                    "index", index + 1,
                    "id", slot.id(),
                    "group", slot.group(),
                    "groupKey", slot.groupKey(),
                    "type", slot.type(),
                    "typeKey", slot.typeKey(),
                    "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                    "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
            ));
        }
        payload.put("targets", targets);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\uff0c\u4e3a targets \u4e2d\u7684\u6bcf\u4e2a\u76ee\u6807\u5404\u751f\u6210 1 \u6761\u63a5\u53e3\u7528\u4f8b\u3002
                \u53ea\u8fd4\u56de\u4e25\u683c JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\u3001\u89e3\u91ca\u6587\u5b57\u6216\u4ee3\u7801\u5757\u3002
                \u8fd4\u56de\u683c\u5f0f\uff1a{"cases":[case, case]}
                \u6bcf\u4e2a case \u5b57\u6bb5\u8981\u5305\u542b name\u3001description\u3001tags\u3001expected\u3001requestConfig\u3001assertions\u3001preProcessors\u3001postProcessors\u3002
                \u89c4\u5219\uff1a
                1. cases \u6570\u91cf\u5fc5\u987b\u7b49\u4e8e targets \u6570\u91cf\uff0c\u5e76\u4e14\u987a\u5e8f\u5fc5\u987b\u548c targets \u4e00\u81f4\u3002
                2. \u6bcf\u6761 case \u7684\u573a\u666f\u5fc5\u987b\u8d34\u5408\u5bf9\u5e94 target.type\u3002
                3. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                4. \u53c2\u6570\u5b57\u6bb5\u5c3d\u91cf\u8865\u5168 required\u3001paramType\u3001minLength\u3001maxLength\u3001description\u3001enabled\u3002
                5. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                6. name \u4e0d\u8981\u5305\u542b\u5206\u7ec4\u524d\u7f00\uff0c\u63a8\u8350\u683c\u5f0f\uff1a\u7c7b\u578b \u2013 \u573a\u666f\u63cf\u8ff0 \u2013 \u671f\u671b\u8fd4\u56de\u7ed3\u679c\u3002
                7. expected \u7528\u4e00\u53e5\u8bdd\u63cf\u8ff0\u65ad\u8a00\u610f\u56fe\uff0c\u4e0d\u8981\u4e3a\u7a7a\u3002
                8. \u6240\u6709\u8f93\u51fa\u5fc5\u987b\u662f\u5408\u6cd5 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    private String buildStreamingBatchPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            ApiAiCaseGenerationSlot slot = slots.get(index);
            targets.add(Map.of(
                    "index", index + 1,
                    "id", slot.id(),
                    "group", slot.group(),
                    "groupKey", slot.groupKey(),
                    "type", slot.type(),
                    "typeKey", slot.typeKey(),
                    "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                    "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
            ));
        }
        payload.put("targets", targets);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\uff0c\u4e3a targets \u4e2d\u7684\u6bcf\u4e2a\u76ee\u6807\u5404\u751f\u6210 1 \u6761\u63a5\u53e3\u7528\u4f8b\u3002
                \u5fc5\u987b\u4f7f\u7528 NDJSON \u8f93\u51fa\uff1a\u4e00\u884c\u4e00\u6761\u5b8c\u6574 JSON\uff0c\u6bcf\u884c\u5bf9\u5e94\u4e00\u4e2a target\u3002\u4e0d\u8981\u8f93\u51fa Markdown\u3001\u4ee3\u7801\u5757\u3001\u89e3\u91ca\u6587\u5b57\u6216 JSON \u6570\u7ec4\u3002
                \u6bcf\u4e00\u884c\u5fc5\u987b\u662f\u5408\u6cd5 JSON\uff0c\u7ed3\u6784\u5982\u4e0b\uff1a
                {"id":"target id","case":{"name":"\u7528\u4f8b\u7c7b\u578b \u2013 \u7528\u4f8b\u573a\u666f \u2013 \u671f\u671b\u7ed3\u679c","description":"\u7528\u4f8b\u8bf4\u660e","tags":["\u6807\u7b7e"],"expected":"\u9884\u671f\u7ed3\u679c","requestConfig":{"method":"GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE","path":"\u63a5\u53e3\u8def\u5f84","timeoutMs":10000,"queryParams":[{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],"headers":[{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],"cookies":[],"body":{"type":"NONE|FORM_DATA|X_WWW_FORM_URLENCODED|RAW_JSON|RAW_XML|RAW_TEXT|BINARY","rawText":"","formItems":[],"contentType":"","fileName":"","binaryBase64":""},"authConfig":{"authType":"NONE|BASIC|DIGEST","basicAuth":{"userName":"","password":""},"digestAuth":{"userName":"","password":""}}},"assertions":[{"type":"STATUS_CODE","subject":"STATUS_CODE","operator":"LT","expectedValue":"400","enabled":true}],"preProcessors":[],"postProcessors":[]}}
                \u89c4\u5219\uff1a
                1. \u6bcf\u4e2a target \u5fc5\u987b\u8f93\u51fa\u4e00\u884c\uff0c\u8f93\u51fa\u987a\u5e8f\u5c3d\u91cf\u548c targets \u4e00\u81f4\u3002
                2. \u6bcf\u884c\u7684 id \u5fc5\u987b\u7b49\u4e8e\u5bf9\u5e94 target.id\u3002
                3. \u6bcf\u6761 case \u7684\u573a\u666f\u5fc5\u987b\u8d34\u5408\u5bf9\u5e94 target.type\u3002
                4. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                5. \u53c2\u6570\u5b57\u6bb5\u5c3d\u91cf\u8865\u5168 required\u3001paramType\u3001minLength\u3001maxLength\u3001description\u3001enabled\u3002
                6. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                7. name \u4e0d\u8981\u5305\u542b\u5206\u7ec4\u524d\u7f00\uff0c\u4f8b\u5982\u4e0d\u8981\u5199\u3010\u6b63\u5411\u3011\u3002\u63a8\u8350\u683c\u5f0f\uff1a\u7c7b\u578b \u2013 \u573a\u666f\u63cf\u8ff0 \u2013 \u671f\u671b\u8fd4\u56de\u7ed3\u679c\u3002
                8. expected \u7528\u4e00\u53e5\u8bdd\u63cf\u8ff0\u65ad\u8a00\u610f\u56fe\uff0c\u4e0d\u8981\u4e3a\u7a7a\uff0cassertions \u8981\u80fd\u9a8c\u8bc1 expected\u3002
                9. \u8f93\u51fa\u8fc7\u7a0b\u4e2d\u4e0d\u8981\u8fd4\u56de {"cases":[...]}\uff0c\u53ea\u8fd4\u56de\u4e00\u884c\u4e00\u6761 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    private ResolvedAiProvider resolveProvider(Long providerConnectionId, String modelName) {
        if (providerConnectionId == null) {
            throw new BadRequestException("\u8bf7\u9009\u62e9 AI \u8fde\u63a5\u6c60\u6a21\u578b");
        }
        AiProviderConnectionEntity connection = aiProviderConnectionMapper.selectOne(new LambdaQueryWrapper<AiProviderConnectionEntity>()
                .eq(AiProviderConnectionEntity::getId, providerConnectionId)
                .eq(AiProviderConnectionEntity::getOwnerUserId, CurrentUserContext.get()));
        if (connection == null) {
            throw new BadRequestException("AI \u8fde\u63a5\u6c60\u914d\u7f6e\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u8bbf\u95ee");
        }
        if (connection.getStatus() != null && connection.getStatus() == 0) {
            throw new BadRequestException("AI \u8fde\u63a5\u6c60\u914d\u7f6e\u5df2\u505c\u7528");
        }
        String resolvedModel = firstNonBlank(modelName, connection.getSelectedModelName(), null);
        if (resolvedModel == null) {
            throw new BadRequestException("\u8bf7\u9009\u62e9 AI \u6a21\u578b");
        }
        String apiKey = aiSecretCodec.decrypt(connection.getApiKeyCipherText());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI \u8fde\u63a5\u6c60\u672a\u914d\u7f6e API Key");
        }
        String protocolType = firstNonBlank(connection.getProtocolType(), AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT);
        return new ResolvedAiProvider(new AiProviderRequestProfile(
                protocolType,
                providerForProtocolType(protocolType),
                resolvedModel,
                connection.getBaseUrl(),
                0.2,
                1.0,
                1,
                connection.getRequestTimeoutSeconds() == null ? defaultRequestTimeoutSeconds : connection.getRequestTimeoutSeconds()
        ), apiKey);
    }

    private WorkspaceEntity resolveWorkspace(String headerWorkspaceCode, String requestWorkspaceCode) {
        String target = WorkspaceScope.normalize(firstNonBlank(requestWorkspaceCode, headerWorkspaceCode, null));
        if (WorkspaceScope.isAll(target)) {
            return workspaceService.requireWritableWorkspace(requestWorkspaceCode);
        }
        return workspaceService.requireWritableWorkspace(target);
    }

    private List<ApiAiCaseGenerationOption> normalizeOptions(List<ApiAiCaseGenerationOption> options) {
        List<ApiAiCaseGenerationOption> normalized = new ArrayList<>();
        for (ApiAiCaseGenerationOption option : defaultList(options, List.<ApiAiCaseGenerationOption>of())) {
            if (option == null || blankToNull(option.key()) == null || blankToNull(option.label()) == null) {
                continue;
            }
            normalized.add(new ApiAiCaseGenerationOption(
                    blankToNull(option.id()),
                    option.key().trim(),
                    firstNonBlank(option.group(), "other"),
                    option.label().trim(),
                    firstNonBlank(option.groupLabel(), option.group(), "\u5176\u4ed6")
            ));
        }
        if (normalized.isEmpty()) {
            throw new BadRequestException("\u8bf7\u81f3\u5c11\u9009\u62e9\u4e00\u79cd\u751f\u6210\u7c7b\u578b");
        }
        return normalized;
    }

    private int resolveTargetCount(String caseCount, int selectedCount) {
        if (caseCount == null || caseCount.isBlank() || "AUTO".equalsIgnoreCase(caseCount)) {
            return Math.min(Math.max(selectedCount, 1), 12);
        }
        try {
            return Math.max(1, Math.min(MAX_GENERATED_CASES, Integer.parseInt(caseCount.trim())));
        } catch (NumberFormatException exception) {
            return Math.min(Math.max(selectedCount, 1), 12);
        }
    }

    private List<ApiAiCaseGenerationSlot> buildSlots(List<ApiAiCaseGenerationOption> options, int targetCount) {
        List<ApiAiCaseGenerationSlot> slots = new ArrayList<>();
        for (int i = 0; i < targetCount; i++) {
            ApiAiCaseGenerationOption option = options.get(i % options.size());
            slots.add(new ApiAiCaseGenerationSlot(
                    firstNonBlank(option.id(), "ai-case-" + System.nanoTime() + "-" + i),
                    option.groupLabel(),
                    option.group(),
                    option.label(),
                    option.key()
            ));
        }
        return slots;
    }

    private void writeEvent(Writer writer, ApiAiCaseGenerationEvent event) throws IOException {
        writer.write("event: ");
        writer.write(event.event());
        writer.write("\n");
        writer.write("data: ");
        writer.write(objectMapper.writeValueAsString(event));
        writer.write("\n\n");
        writer.flush();
    }

    private String providerForProtocolType(String protocolType) {
        String normalized = protocolType == null ? "" : protocolType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case AiProviderClient.PROTOCOL_AZURE_OPENAI -> "Azure OpenAI";
            case AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_RESPONSES -> "OpenAI Responses Compatible";
            default -> "OpenAI Compatible";
        };
    }

    private ApiAuthConfigInput normalizeAuth(ApiAuthConfigInput authConfig) {
        if (authConfig == null) {
            return new ApiAuthConfigInput("NONE", new ApiAuthCredentialInput("", ""), new ApiAuthCredentialInput("", ""));
        }
        return new ApiAuthConfigInput(
                firstNonBlank(authConfig.authType(), "NONE").toUpperCase(Locale.ROOT),
                normalizeCredential(authConfig.basicAuth()),
                normalizeCredential(authConfig.digestAuth())
        );
    }

    private ApiAuthCredentialInput normalizeCredential(ApiAuthCredentialInput credential) {
        if (credential == null) {
            return new ApiAuthCredentialInput("", "");
        }
        return new ApiAuthCredentialInput(Optional.ofNullable(credential.userName()).orElse(""),
                Optional.ofNullable(credential.password()).orElse(""));
    }

    private List<String> normalizeTags(List<String> tags, ApiAiCaseGenerationSlot slot) {
        List<String> normalized = new ArrayList<>();
        for (String tag : defaultList(tags, List.<String>of())) {
            if (blankToNull(tag) != null && normalized.size() < 6) {
                normalized.add(tag.trim());
            }
        }
        if (!normalized.contains(slot.group())) {
            normalized.add(slot.group());
        }
        if (!normalized.contains(slot.type())) {
            normalized.add(slot.type());
        }
        return normalized;
    }

    private String defaultDescription(ApiAiCaseGenerationSlot slot) {
        return "AI \u751f\u6210\u7684" + slot.group() + "\u63a5\u53e3\u7528\u4f8b";
    }

    private String defaultExpected(ApiAiCaseGenerationSlot slot) {
        return "positive".equals(slot.groupKey())
                ? "\u9884\u671f\u63a5\u53e3\u8fd4\u56de\u6210\u529f\u54cd\u5e94"
                : "\u9884\u671f\u63a5\u53e3\u8fd4\u56de\u5408\u7406\u9519\u8bef\u6216\u88ab\u89c4\u5219\u62e6\u622a";
    }

    private String normalizeCaseName(String rawName, ApiAiCaseGenerationSlot slot, String method, String path, String expected) {
        String name = firstNonBlank(rawName, slot.type() + " \u2013 " + method + " " + path + " \u2013 " + expected);
        name = name
                .replaceFirst("^\\u3010[^\\u3011]+\\u3011\\s*", "")
                .replaceFirst("^\\[[^\\]]+]\\s*", "")
                .replaceFirst("^(\\u6b63\\u5411|\\u53cd\\u5411|\\u8d1f\\u5411|\\u8fb9\\u754c|\\u5b89\\u5168\\u6027|\\u5b89\\u5168)\\s*[-\\u2013\\u2014:\\uff1a]\\s*", "")
                .trim();
        if (!name.startsWith(slot.type() + " \u2013 ") && !name.startsWith(slot.type() + " - ")) {
            name = slot.type() + " \u2013 " + name;
        }
        return name;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String stageFailureMessage(String stage, Exception exception) {
        return stage + "\uff1a" + exceptionMessage(exception);
    }

    private String exceptionMessage(Exception exception) {
        if (exception == null) {
            return "\u672a\u77e5\u9519\u8bef";
        }
        String message = firstNonBlank(exception.getMessage(),
                exception.getCause() == null ? null : exception.getCause().getMessage(),
                exception.getClass().getSimpleName());
        return message == null ? "\u672a\u77e5\u9519\u8bef" : message;
    }

    private <T> List<T> defaultList(List<T> primary, List<T> fallback) {
        return primary == null ? (fallback == null ? List.of() : fallback) : primary;
    }

    private String firstNonBlank(String first, String fallback) {
        return firstNonBlank(first, fallback, null);
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private String firstNonBlank(String first, String second, String third, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        if (third != null && !third.isBlank()) return third.trim();
        return fallback;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ApiAiCaseGenerationRequest(
            String workspaceCode,
            Long definitionId,
            String definitionName,
            String name,
            String method,
            String path,
            String description,
            Long providerConnectionId,
            String modelName,
            String caseCount,
            Boolean noDuplicate,
            String prompt,
            List<ApiAiCaseGenerationOption> options,
            ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors,
            List<ApiAiExistingCaseSummary> existingCases
    ) {
    }

    public record ApiAiCaseGenerationOption(
            String id,
            String key,
            String group,
            String label,
            String groupLabel
    ) {
    }

    public record ApiAiExistingCaseSummary(
            Long id,
            String name,
            List<String> tags
    ) {
    }

    public record ApiAiCaseGenerationEvent(
            String event,
            String itemId,
            String group,
            String type,
            Integer total,
            ApiAiGeneratedCaseDraft item,
            ApiAiGeneratedCaseOutline outline,
            String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAiGeneratedCaseDraft(
            String name,
            String description,
            List<String> tags,
            String group,
            String groupKey,
            String type,
            String typeKey,
            String expected,
            ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAiGeneratedCaseOutline(
            String name,
            String description,
            List<String> tags,
            String group,
            String groupKey,
            String type,
            String typeKey,
            String expected
    ) {
    }

    private record ApiAiCaseGenerationSlot(
            String id,
            String group,
            String groupKey,
            String type,
            String typeKey
    ) {
    }

    private record ApiAiGeneratedCaseLine(
            String id,
            ApiAiGeneratedCaseDraft draft
    ) {
    }

    private record ApiAiGeneratedCaseOutlineLine(
            String id,
            ApiAiGeneratedCaseOutline outline
    ) {
    }

    private record ResolvedAiProvider(
            AiProviderRequestProfile profile,
            String apiKey
    ) {
    }
}
