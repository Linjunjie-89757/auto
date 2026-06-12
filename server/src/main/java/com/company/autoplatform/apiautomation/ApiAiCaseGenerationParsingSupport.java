package com.company.autoplatform.apiautomation;

import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiGeneratedCaseDraft;
import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiGeneratedCaseOutline;
import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiGeneratedCaseOutlineLine;
import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
class ApiAiCaseGenerationParsingSupport {

    private final ObjectMapper objectMapper;

    ApiAiCaseGenerationParsingSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ApiAiGeneratedCaseOutlineLine parseGeneratedCaseOutlineLine(String line) {
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

    ApiAiGeneratedCaseDraft parseDraft(String content) {
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

    List<ApiAiGeneratedCaseOutlineLine> parseOutlinesFromNdjson(String content) {
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

    String optionalText(JsonNode item, String field) {
        JsonNode fieldNode = item == null ? null : item.path(field);
        if (fieldNode == null || fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        String value = fieldNode.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
