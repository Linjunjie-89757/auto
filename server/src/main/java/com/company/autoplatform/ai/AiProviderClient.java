package com.company.autoplatform.ai;

import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AiProviderClient {
    private static final String PROTOCOL_OPENAI_CHAT = "OPENAI_CHAT_COMPLETIONS";
    private static final String PROTOCOL_OPENAI_RESPONSES = "OPENAI_RESPONSES";
    private static final String PROTOCOL_AZURE_OPENAI = "AZURE_OPENAI";
    private static final int LOG_BODY_LIMIT = 1000;

    private static final Logger log = LoggerFactory.getLogger(AiProviderClient.class);

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
        String protocolType = resolveProtocolType(config);
        String endpoint = resolveEndpointByProtocol(config.getBaseUrl(), protocolType);
        try {
            String requestBody = buildRequestBodyByProtocol(config, protocolType, prompt, images, false);
            HttpResponse<String> response = sendRequest(endpoint, apiKey, protocolType, requestBody);
            if (response.statusCode() >= 400) {
                log.warn(
                        "AI provider returned error response. protocolType={}, model={}, endpoint={}, imageCount={}, status={}, body={}",
                        protocolType,
                        config.getModel(),
                        endpoint,
                        images == null ? 0 : images.size(),
                        response.statusCode(),
                        abbreviate(response.body())
                );
                if (requiresImageCapability(response.body(), images)) {
                    throw new BadRequestException("The current AI model or service does not support image input");
                }
                if (requiresStreamFallback(response.body())) {
                    return requestStructuredContentStream(endpoint, protocolType, config, apiKey, prompt, images);
                }
                throw new BadRequestException("AI provider request failed: " + response.body());
            }
            return extractContentByProtocol(protocolType, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error(
                    "AI provider request interrupted. protocolType={}, model={}, endpoint={}, imageCount={}, promptLength={}, exception={}: {}",
                    protocolType,
                    config.getModel(),
                    endpoint,
                    images == null ? 0 : images.size(),
                    prompt == null ? 0 : prompt.length(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
            throw new BadRequestException("AI provider request failed");
        } catch (IOException exception) {
            log.error(
                    "AI provider request I/O failure. protocolType={}, model={}, endpoint={}, imageCount={}, promptLength={}, exception={}: {}",
                    protocolType,
                    config.getModel(),
                    endpoint,
                    images == null ? 0 : images.size(),
                    prompt == null ? 0 : prompt.length(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );
            throw new BadRequestException("AI provider request failed");
        }
    }

    private String requestStructuredContentStream(
            String endpoint,
            String protocolType,
            AiCaseConfigEntity config,
            String apiKey,
            String prompt,
            List<ImageInput> images
    ) throws IOException, InterruptedException {
        if (!PROTOCOL_OPENAI_CHAT.equals(protocolType) && !PROTOCOL_AZURE_OPENAI.equals(protocolType)) {
            throw new BadRequestException("The selected AI protocol does not support stream fallback");
        }
        String requestBody = buildRequestBodyByProtocol(config, protocolType, prompt, images, true);
        HttpResponse<String> response = sendRequest(endpoint, apiKey, protocolType, requestBody);
        if (response.statusCode() >= 400) {
            log.warn(
                    "AI provider stream fallback returned error response. protocolType={}, model={}, endpoint={}, imageCount={}, status={}, body={}",
                    protocolType,
                    config.getModel(),
                    endpoint,
                    images == null ? 0 : images.size(),
                    response.statusCode(),
                    abbreviate(response.body())
            );
            throw new BadRequestException("AI provider request failed: " + response.body());
        }
        String mergedContent = mergeStreamingContent(response.body());
        if (mergedContent.isBlank()) {
            throw new BadRequestException("AI provider returned empty stream content");
        }
        return stripJsonFence(mergedContent);
    }

    private String buildRequestBodyByProtocol(
            AiCaseConfigEntity config,
            String protocolType,
            String prompt,
            List<ImageInput> images,
            boolean stream
    ) throws IOException {
        if (PROTOCOL_OPENAI_RESPONSES.equals(protocolType)) {
            return buildResponsesRequestBody(config, prompt, images);
        }
        return buildChatCompletionsRequestBody(config, prompt, images, stream);
    }

    private String buildChatCompletionsRequestBody(
            AiCaseConfigEntity config,
            String prompt,
            List<ImageInput> images,
            boolean stream
    ) throws IOException {
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

    private String buildResponsesRequestBody(AiCaseConfigEntity config, String prompt, List<ImageInput> images) throws IOException {
        List<Object> inputContent = new ArrayList<>();
        inputContent.add(Map.of("type", "input_text", "text", prompt));
        if (images != null) {
            for (ImageInput image : images) {
                inputContent.add(Map.of(
                        "type", "input_image",
                        "image_url", buildDataUrl(image)
                ));
            }
        }
        return objectMapper.writeValueAsString(Map.of(
                "model", config.getModel(),
                "temperature", config.getTemperature(),
                "instructions", "You are a QA assistant that outputs only structured JSON.",
                "input", List.of(
                        Map.of(
                                "role", "user",
                                "content", inputContent
                        )
                )
        ));
    }

    private String buildDataUrl(ImageInput image) {
        return "data:" + image.contentType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
    }

    private HttpResponse<String> sendRequest(String endpoint, String apiKey, String protocolType, String requestBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json");

        if (PROTOCOL_AZURE_OPENAI.equals(protocolType)) {
            builder.header("api-key", apiKey);
        } else {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return httpClient.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String extractContentByProtocol(String protocolType, String responseBody) throws IOException {
        if (PROTOCOL_OPENAI_RESPONSES.equals(protocolType)) {
            return extractResponsesContent(responseBody);
        }
        return extractChatCompletionsContent(responseBody);
    }

    private String extractChatCompletionsContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new BadRequestException("AI provider returned empty content");
        }
        return stripJsonFence(extractContent(contentNode));
    }

    private String extractResponsesContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return stripJsonFence(outputText.asText());
        }

        JsonNode output = root.path("output");
        if (!output.isArray()) {
            throw new BadRequestException("AI provider returned empty content");
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode textNode = contentItem.path("text");
                if (textNode.isTextual()) {
                    builder.append(textNode.asText());
                }
            }
        }
        String merged = builder.toString().trim();
        if (merged.isEmpty()) {
            throw new BadRequestException("AI provider returned empty content");
        }
        return stripJsonFence(merged);
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

    private String resolveEndpointByProtocol(String baseUrl, String protocolType) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("AI base URL is required");
        }
        if (PROTOCOL_OPENAI_RESPONSES.equals(protocolType)) {
            if (trimmed.contains("/responses")) {
                return trimmed;
            }
            if (trimmed.endsWith("/")) {
                return trimmed + "responses";
            }
            return trimmed + "/responses";
        }
        if (trimmed.contains("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private String resolveProtocolType(AiCaseConfigEntity config) {
        String normalized = normalizeProtocolType(config.getProtocolType());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        String provider = normalizeProvider(config.getProvider());
        if ("AZURE_OPENAI".equals(provider)) {
            return PROTOCOL_AZURE_OPENAI;
        }
        if ("INTERNAL_PROXY".equals(provider)) {
            String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl().trim().toLowerCase(Locale.ROOT);
            return baseUrl.contains("/responses") ? PROTOCOL_OPENAI_RESPONSES : PROTOCOL_OPENAI_CHAT;
        }
        return PROTOCOL_OPENAI_CHAT;
    }

    private AiGeneratedCasesResult parseGeneratedCases(String normalizedJson, Integer maxCases) {
        try {
            JsonNode parsed = objectMapper.readTree(normalizedJson);
            JsonNode casesNode = parsed.isArray() ? parsed : parsed.path("cases");
            if (!casesNode.isArray()) {
                throw new BadRequestException("AI response cannot be parsed as structured test cases");
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
                    invalidCases.add(new AiInvalidCaseItem(index, fallbackTitle(item, index), "Missing case title"));
                    continue;
                }
                if (steps == null || steps.isBlank()) {
                    invalidCases.add(new AiInvalidCaseItem(index, title, "Missing test steps"));
                    continue;
                }
                if (expectedResult == null || expectedResult.isBlank()) {
                    invalidCases.add(new AiInvalidCaseItem(index, title, "Missing expected result"));
                    continue;
                }
                String normalizedCaseType = normalizeCaseType(optionalText(item, "caseType"), itemWarnings);
                String normalizedPriority = normalizePriority(optionalText(item, "priority"), itemWarnings);
                if (!itemWarnings.isEmpty()) {
                    warnings.add("Candidate case " + index + " has normalized fields");
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
                throw new BadRequestException("AI response was parsed, but no valid test cases were found");
            }
            return new AiGeneratedCasesResult(items, warnings, invalidCases, normalizedJson);
        } catch (IOException exception) {
            throw new BadRequestException("AI response cannot be parsed as structured test cases");
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
                summary = "AI review completed. Please combine the issues and suggestions to decide next steps.";
            }
            return new AiReviewResult(result, summary, issues, suggestions, normalizedJson, true);
        } catch (IOException exception) {
            return new AiReviewResult(
                    "SUGGEST",
                    "AI returned a non-structured review result. Please inspect the raw content.",
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

    private String normalizeProtocolType(String protocolType) {
        return protocolType == null ? "" : protocolType.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String normalizeCaseType(String caseType, List<String> warnings) {
        if (caseType == null || caseType.isBlank()) {
            warnings.add("caseType is missing, defaulted to FUNCTION");
            return "FUNCTION";
        }
        String normalized = caseType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FUNCTION", "BOUNDARY", "EXCEPTION", "REGRESSION" -> normalized;
            default -> {
                warnings.add("caseType '" + caseType + "' is not recognized, defaulted to FUNCTION");
                yield "FUNCTION";
            }
        };
    }

    private String normalizePriority(String priority, List<String> warnings) {
        if (priority == null || priority.isBlank()) {
            warnings.add("priority is missing, defaulted to P1");
            return "P1";
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "P1", "P2", "P3" -> normalized;
            default -> {
                warnings.add("priority '" + priority + "' is not recognized, defaulted to P1");
                yield "P1";
            }
        };
    }

    private String fallbackTitle(JsonNode item, int index) {
        String value = optionalText(item, "title");
        return value == null || value.isBlank() ? "Candidate case " + index : value;
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

    private String abbreviate(String value) {
        if (value == null || value.length() <= LOG_BODY_LIMIT) {
            return value;
        }
        return value.substring(0, LOG_BODY_LIMIT) + "...(truncated)";
    }

    public record ImageInput(
            String fileName,
            String contentType,
            byte[] bytes
    ) {
    }
}
