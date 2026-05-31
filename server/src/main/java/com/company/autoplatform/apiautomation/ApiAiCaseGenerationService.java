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

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Service
public class ApiAiCaseGenerationService {

    private static final int MAX_GENERATED_CASES = 80;

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
            writeEvent(writer, new ApiAiCaseGenerationEvent("started", null, null, null, slots.size(), null, null));
            int completed = 0;
            for (int index = 0; index < slots.size(); index++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new BadRequestException("AI 生成已中断");
                }
                ApiAiCaseGenerationSlot slot = slots.get(index);
                writeEvent(writer, new ApiAiCaseGenerationEvent("item_generating", slot.id(), slot.group(), slot.type(), slots.size(), null, null));
                try {
                    ApiAiGeneratedCaseDraft draft = generateOneCase(workspace, provider, request, slot, index + 1, slots.size());
                    completed += 1;
                    writeEvent(writer, new ApiAiCaseGenerationEvent("item_completed", slot.id(), slot.group(), slot.type(), slots.size(), draft, null));
                } catch (Exception exception) {
                    writeEvent(writer, new ApiAiCaseGenerationEvent("item_failed", slot.id(), slot.group(), slot.type(), slots.size(), null, exception.getMessage()));
                }
            }
            writeEvent(writer, new ApiAiCaseGenerationEvent("completed", null, null, null, slots.size(), null,
                    completed == slots.size() ? null : "部分用例生成失败"));
        } catch (Exception exception) {
            writeEvent(writer, new ApiAiCaseGenerationEvent("failed", null, null, null, null, null, exception.getMessage()));
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

    private ApiAiGeneratedCaseDraft parseDraft(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode draftNode = parsed.has("case") ? parsed.path("case") : parsed;
            ApiAiGeneratedCaseDraft draft = objectMapper.treeToValue(draftNode, ApiAiGeneratedCaseDraft.class);
            if (draft == null) {
                throw new BadRequestException("AI 返回内容为空");
            }
            return draft;
        } catch (IOException exception) {
            throw new BadRequestException("AI 返回内容无法解析为接口用例 JSON");
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
        return new ApiAiGeneratedCaseDraft(
                firstNonBlank(draft.name(), slot.type() + " - " + method + " " + path),
                blankToNull(draft.description()) == null ? defaultDescription(slot) : draft.description().trim(),
                normalizeTags(draft.tags(), slot),
                slot.group(),
                slot.groupKey(),
                slot.type(),
                slot.typeKey(),
                firstNonBlank(draft.expected(), defaultExpected(slot)),
                requestConfig,
                assertions,
                defaultList(draft.preProcessors(), request.preProcessors()),
                defaultList(draft.postProcessors(), request.postProcessors())
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
                "name", firstNonBlank(request.definitionName(), request.name(), "未命名接口"),
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
                "type", slot.type(),
                "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
        ));
        return """
                你是接口自动化测试专家。请基于下面的接口定义，生成 1 条接口自动化用例。
                必须只返回 JSON，不要 Markdown，不要解释。
                返回结构必须是：
                {
                  "name": "用例名称",
                  "description": "用例说明",
                  "tags": ["标签"],
                  "expected": "预期结果",
                  "requestConfig": {
                    "method": "GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE",
                    "path": "接口路径",
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
                规则：
                1. 保持接口 method/path 不变，除非生成项明确需要改参数或请求体。
                2. 根据字段 required、paramType、minLength、maxLength 生成合理参数。
                3. 不要生成与 existingCases 明显重复的用例。
                4. assertions 使用系统已有字段，至少包含一个状态码断言。
                5. 只返回单个 JSON 对象。

                输入：
                %s
                """.formatted(toJson(payload));
    }

    private ResolvedAiProvider resolveProvider(Long providerConnectionId, String modelName) {
        if (providerConnectionId == null) {
            throw new BadRequestException("请选择 AI 连接池模型");
        }
        AiProviderConnectionEntity connection = aiProviderConnectionMapper.selectOne(new LambdaQueryWrapper<AiProviderConnectionEntity>()
                .eq(AiProviderConnectionEntity::getId, providerConnectionId)
                .eq(AiProviderConnectionEntity::getOwnerUserId, CurrentUserContext.get()));
        if (connection == null) {
            throw new BadRequestException("AI 连接池配置不存在或无权访问");
        }
        if (connection.getStatus() != null && connection.getStatus() == 0) {
            throw new BadRequestException("AI 连接池配置已停用");
        }
        String resolvedModel = firstNonBlank(modelName, connection.getSelectedModelName(), null);
        if (resolvedModel == null) {
            throw new BadRequestException("请选择 AI 模型");
        }
        String apiKey = aiSecretCodec.decrypt(connection.getApiKeyCipherText());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("AI 连接池未配置 API Key");
        }
        String protocolType = firstNonBlank(connection.getProtocolType(), AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT);
        return new ResolvedAiProvider(new AiProviderRequestProfile(
                protocolType,
                providerForProtocolType(protocolType),
                resolvedModel,
                connection.getBaseUrl(),
                0.2,
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
                    option.key().trim(),
                    firstNonBlank(option.group(), "other"),
                    option.label().trim(),
                    firstNonBlank(option.groupLabel(), option.group(), "其他")
            ));
        }
        if (normalized.isEmpty()) {
            throw new BadRequestException("请至少选择一种生成类型");
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
                    "ai-case-" + System.nanoTime() + "-" + i,
                    option.groupLabel(),
                    option.group(),
                    option.label(),
                    option.key()
            ));
        }
        return slots;
    }

    private void writeEvent(Writer writer, ApiAiCaseGenerationEvent event) throws IOException {
        writer.write(objectMapper.writeValueAsString(event));
        writer.write("\n");
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
        return "AI 生成的" + slot.group() + "接口用例";
    }

    private String defaultExpected(ApiAiCaseGenerationSlot slot) {
        return "positive".equals(slot.groupKey()) ? "预期接口返回成功响应" : "预期接口返回合理错误或被规则拦截";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
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

    private record ApiAiCaseGenerationSlot(
            String id,
            String group,
            String groupKey,
            String type,
            String typeKey
    ) {
    }

    private record ResolvedAiProvider(
            AiProviderRequestProfile profile,
            String apiKey
    ) {
    }
}
