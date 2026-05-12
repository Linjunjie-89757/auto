package com.company.autoplatform.ai;

import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AiProviderClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public AiProviderClient(@Value("${app.ai.request-timeout-seconds:60}") long timeoutSeconds) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .build();
    }

    public AiGeneratedCasesResult generate(AiCaseConfigEntity config, String apiKey, String prompt, List<ImageInput> images) {
        String content = requestStructuredContent(config, apiKey, prompt, images);
        return parseGeneratedCases(content, config.getMaxCases());
    }

    public AiReviewResult review(AiCaseConfigEntity config, String apiKey, String prompt) {
        String content = requestStructuredContent(config, apiKey, prompt, List.of());
        return parseReviewResult(content);
    }

    public void testConnection(AiCaseConfigEntity config, String apiKey) {
        String content = requestStructuredContent(config, apiKey, buildConnectionProbePrompt(), List.of());
        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode okNode = parsed.path("ok");
            if (!okNode.isBoolean() || !okNode.asBoolean()) {
                throw new BadRequestException("AI provider returned an unexpected probe result");
            }
        } catch (IOException exception) {
            throw new BadRequestException("AI provider probe result is not valid JSON");
        }
    }

    private String requestStructuredContent(AiCaseConfigEntity config, String apiKey, String prompt, List<ImageInput> images) {
        String endpoint = resolveEndpoint(config.getBaseUrl());
        try {
            String requestBody = buildRequestBody(config, prompt, images, false);
            HttpResponse<String> response = sendChatRequest(endpoint, apiKey, config.getProvider(), requestBody);
            if (response.statusCode() >= 400) {
                if (requiresImageCapability(response.body(), images)) {
                    throw new BadRequestException("当前 AI 模型或服务不支持图片输入，请切换支持图像理解的多模态模型后再试");
                }
                if (requiresStreamFallback(response.body())) {
                    return requestStructuredContentStream(endpoint, config, apiKey, prompt, images);
                }
                throw new BadRequestException("AI provider request failed: " + response.body());
            }
            return extractStructuredContent(response.body());
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("AI provider request failed");
        }
    }

    private String requestStructuredContentStream(String endpoint, AiCaseConfigEntity config, String apiKey, String prompt, List<ImageInput> images)
            throws IOException, InterruptedException {
        String requestBody = buildRequestBody(config, prompt, images, true);
        HttpResponse<String> response = sendChatRequest(endpoint, apiKey, config.getProvider(), requestBody);
        if (response.statusCode() >= 400) {
            throw new BadRequestException("AI provider request failed: " + response.body());
        }
        String mergedContent = mergeStreamingContent(response.body());
        if (mergedContent.isBlank()) {
            throw new BadRequestException("AI provider returned empty stream content");
        }
        return stripJsonFence(mergedContent);
    }

    private String buildRequestBody(AiCaseConfigEntity config, String prompt, List<ImageInput> images, boolean stream) throws IOException {
        List<Object> userContent = new ArrayList<>();
        userContent.add(Map.of("type", "text", "text", prompt));
        for (ImageInput image : images) {
            userContent.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", buildDataUrl(image))
            ));
        }
        return objectMapper.writeValueAsString(Map.of(
                "model", config.getModel(),
                "temperature", config.getTemperature(),
                "stream", stream,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a QA assistant that outputs only structured JSON."),
                        Map.of("role", "user", "content", userContent)
                )
        ));
    }

    private String buildDataUrl(ImageInput image) {
        return "data:" + image.contentType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
    }

    private HttpResponse<String> sendChatRequest(String endpoint, String apiKey, String provider, String requestBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");

        if ("AZURE_OPENAI".equalsIgnoreCase(normalizeProvider(provider))) {
            builder.header("api-key", apiKey);
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String extractStructuredContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new BadRequestException("AI provider returned empty content");
        }
        return stripJsonFence(extractContent(contentNode));
    }

    private boolean requiresStreamFallback(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String lower = responseBody.toLowerCase(Locale.ROOT);
        return lower.contains("only support stream mode") || lower.contains("enable the stream parameter");
    }

    private boolean requiresImageCapability(String responseBody, List<ImageInput> images) {
        if (images == null || images.isEmpty() || responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String lower = responseBody.toLowerCase(Locale.ROOT);
        return lower.contains("unknown variant")
                || lower.contains("expected `text`")
                || (lower.contains("failed to deserialize") && lower.contains("image"))
                || lower.contains("image_url");
    }

    private String mergeStreamingContent(String responseBody) throws IOException {
        StringBuilder builder = new StringBuilder();
        String[] lines = responseBody.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || !line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }
            JsonNode root = objectMapper.readTree(payload);
            JsonNode delta = root.path("choices").path(0).path("delta");
            if (delta.isMissingNode() || delta.isNull()) {
                continue;
            }
            JsonNode contentNode = delta.path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                continue;
            }
            builder.append(extractContent(contentNode));
        }
        return builder.toString();
    }

    private String buildConnectionProbePrompt() {
        return """
                Return only JSON.
                Reply exactly with:
                {"ok":true}
                """;
    }

    private String resolveEndpoint(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("AI base URL is required");
        }
        if (trimmed.contains("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private AiGeneratedCasesResult parseGeneratedCases(String normalizedJson, Integer maxCases) {
        try {
            JsonNode parsed = objectMapper.readTree(normalizedJson);
            JsonNode casesNode = parsed.isArray() ? parsed : parsed.path("cases");
            if (!casesNode.isArray()) {
                throw new BadRequestException("AI 返回结果无法解析为结构化用例，请调整 Prompt 或稍后重试");
            }

            List<GeneratedAiCaseItem> items = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<AiInvalidCaseItem> invalidCases = new ArrayList<>();
            int limit = maxCases == null ? Integer.MAX_VALUE : maxCases;
            int index = 0;
            for (JsonNode item : casesNode) {
                if (items.size() >= limit) {
                    break;
                }
                index += 1;
                List<String> itemWarnings = new ArrayList<>();
                String title = optionalText(item, "title");
                String steps = optionalText(item, "steps");
                String expectedResult = optionalText(item, "expectedResult");
                if (title == null || title.isBlank()) {
                    invalidCases.add(new AiInvalidCaseItem(index, fallbackTitle(item, index), "缺少用例名称"));
                    continue;
                }
                if (steps == null || steps.isBlank()) {
                    invalidCases.add(new AiInvalidCaseItem(index, title, "缺少测试步骤"));
                    continue;
                }
                if (expectedResult == null || expectedResult.isBlank()) {
                    invalidCases.add(new AiInvalidCaseItem(index, title, "缺少预期结果"));
                    continue;
                }
                String normalizedCaseType = normalizeCaseType(optionalText(item, "caseType"), itemWarnings);
                String normalizedPriority = normalizePriority(optionalText(item, "priority"), itemWarnings);
                if (!itemWarnings.isEmpty()) {
                    warnings.add("第 " + index + " 条候选用例已自动修正部分字段");
                }
                items.add(new GeneratedAiCaseItem(
                        title,
                        normalizedCaseType,
                        normalizedPriority,
                        optionalText(item, "precondition"),
                        steps,
                        expectedResult,
                        optionalText(item, "riskNotes"),
                        itemWarnings,
                        false,
                        null,
                        null
                ));
            }
            if (items.isEmpty()) {
                throw new BadRequestException("AI 返回结果已解析，但没有可用用例，请检查 Prompt 或补充需求描述");
            }
            return new AiGeneratedCasesResult(items, warnings, invalidCases, normalizedJson);
        } catch (IOException exception) {
            throw new BadRequestException("AI 返回结果无法解析为结构化用例，请调整 Prompt 或稍后重试");
        }
    }

    private AiReviewResult parseReviewResult(String normalizedJson) {
        try {
            JsonNode parsed = objectMapper.readTree(normalizedJson);
            String result = normalizeReviewResult(optionalText(parsed, "result"));
            String summary = optionalText(parsed, "summary");
            List<String> issues = stringList(parsed.path("issues"));
            List<String> suggestions = stringList(parsed.path("suggestions"));
            if ((summary == null || summary.isBlank()) && !issues.isEmpty()) {
                summary = issues.get(0);
            }
            if ((summary == null || summary.isBlank()) && !suggestions.isEmpty()) {
                summary = suggestions.get(0);
            }
            if (summary == null || summary.isBlank()) {
                summary = "AI 已完成评审，请结合问题和建议决定是否采纳。";
            }
            return new AiReviewResult(result, summary, issues, suggestions, normalizedJson, true);
        } catch (IOException exception) {
            return new AiReviewResult(
                    "SUGGEST",
                    "AI 返回了非结构化评审结果，请参考原文后再决定是否采纳。",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    normalizedJson,
                    false
            );
        }
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                JsonNode textNode = node.path("text");
                if (textNode.isTextual()) {
                    builder.append(textNode.asText());
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > 0 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String optionalText(JsonNode item, String field) {
        JsonNode fieldNode = item.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode child : fieldNode) {
                String value = child == null ? null : child.asText();
                if (value != null && !value.trim().isBlank()) {
                    values.add(value.trim());
                }
            }
            return values.isEmpty() ? null : String.join("\n", values);
        }
        String value = fieldNode.asText();
        return value == null ? null : value.trim();
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String normalizeCaseType(String caseType, List<String> warnings) {
        if (caseType == null || caseType.isBlank()) {
            warnings.add("类型缺失，已回退为 FUNCTION");
            return "FUNCTION";
        }
        String normalized = caseType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FUNCTION", "BOUNDARY", "EXCEPTION", "REGRESSION" -> normalized;
            default -> {
                warnings.add("类型“" + caseType + "”无法识别，已回退为 FUNCTION");
                yield "FUNCTION";
            }
        };
    }

    private String normalizePriority(String priority, List<String> warnings) {
        if (priority == null || priority.isBlank()) {
            warnings.add("优先级缺失，已回退为 P1");
            return "P1";
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "P1", "P2", "P3" -> normalized;
            default -> {
                warnings.add("优先级“" + priority + "”无法识别，已回退为 P1");
                yield "P1";
            }
        };
    }

    private String fallbackTitle(JsonNode item, int index) {
        String value = optionalText(item, "title");
        return value == null || value.isBlank() ? "候选用例 " + index : value;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private String normalizeReviewResult(String result) {
        if (result == null || result.isBlank()) {
            return "SUGGEST";
        }
        String normalized = result.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVE", "REJECT", "SUGGEST" -> normalized;
            default -> "SUGGEST";
        };
    }

    public record ImageInput(
            String fileName,
            String contentType,
            byte[] bytes
    ) {
    }
}
