package com.company.autoplatform.ai;

import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiProviderClient {
    public static final String PROTOCOL_OPENAI_COMPATIBLE_CHAT = "OPENAI_COMPATIBLE_CHAT";
    public static final String PROTOCOL_OPENAI_COMPATIBLE_RESPONSES = "OPENAI_COMPATIBLE_RESPONSES";
    public static final String PROTOCOL_AZURE_OPENAI = "AZURE_OPENAI";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AiProtocolAdapter> adapters;

    public AiProviderClient(List<AiProtocolAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toMap(AiProtocolAdapter::protocolType, Function.identity()));
    }

    public AiGeneratedCasesResult generate(
            AiProviderRequestProfile profile,
            String apiKey,
            String prompt,
            List<ImageInput> images
    ) {
        String content = adapter(profile.protocolType()).requestStructuredContent(profile, apiKey, prompt, images);
        return parseGeneratedCases(content, profile.maxCases());
    }

    public AiReviewResult review(AiProviderRequestProfile profile, String apiKey, String prompt) {
        String content = adapter(profile.protocolType()).requestStructuredContent(profile, apiKey, prompt, List.of());
        return parseReviewResult(content);
    }

    public void testConnection(AiProviderRequestProfile profile, String apiKey) {
        adapter(profile.protocolType()).testConnection(profile, apiKey);
    }

    public AiModelFetchResult fetchModels(AiProviderRequestProfile profile, String apiKey) {
        return adapter(profile.protocolType()).fetchModels(profile, apiKey);
    }

    public AiModelCapabilities probeCapabilities(AiProviderRequestProfile profile, String apiKey) {
        return adapter(profile.protocolType()).probeCapabilities(profile, apiKey);
    }

    private AiProtocolAdapter adapter(String protocolType) {
        AiProtocolAdapter adapter = adapters.get(protocolType);
        if (adapter == null) {
            throw new BadRequestException("暂不支持该 AI 协议类型");
        }
        return adapter;
    }

    private AiGeneratedCasesResult parseGeneratedCases(String normalizedJson, Integer maxCases) {
        try {
            JsonNode parsed = objectMapper.readTree(normalizedJson);
            JsonNode casesNode = parsed.isArray() ? parsed : parsed.path("cases");
            if (!casesNode.isArray()) {
                throw new BadRequestException("AI 返回内容无法解析为结构化用例");
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
                throw new BadRequestException("AI 返回已解析，但没有得到有效用例");
            }
            return new AiGeneratedCasesResult(items, warnings, invalidCases, normalizedJson);
        } catch (IOException exception) {
            throw new BadRequestException("AI 返回内容无法解析为结构化用例");
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

    private String normalizeCaseType(String caseType, List<String> warnings) {
        if (caseType == null || caseType.isBlank()) {
            warnings.add("caseType is missing, defaulted to FUNCTION");
            return "FUNCTION";
        }
        String normalized = caseType.trim().toUpperCase();
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
        String normalized = priority.trim().toUpperCase();
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
        String normalized = result.trim().toUpperCase();
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
