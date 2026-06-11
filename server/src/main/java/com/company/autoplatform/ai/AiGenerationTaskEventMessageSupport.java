package com.company.autoplatform.ai;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiGenerationTaskEventMessageSupport {

    private static final Pattern INTERNAL_CASE_INDEX_PATTERN = Pattern.compile("(?i)\\b(?:caseIndex|itemIndex|Index|Case)\\s*[:#=]?\\s*(\\d+)\\b");

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String reviewStatusLabel(String status) {
        if ("APPROVED".equals(status)) {
            return "通过";
        }
        if ("OPTIMIZED".equals(status)) {
            return "已优化";
        }
        if ("SUPPLEMENTED".equals(status)) {
            return "已补充";
        }
        if ("CONFIRM_REQUIRED".equals(status)) {
            return "建议确认";
        }
        if ("NOT_RECOMMENDED".equals(status)) {
            return "不推荐";
        }
        if ("REJECTED".equals(status)) {
            return "需重生成";
        }
        return "建议优化";
    }

    String buildGeneratedCaseEventMessage(Integer itemIndex, GeneratedAiCaseItem item) {
        StringBuilder message = new StringBuilder("第 ")
                .append((itemIndex == null ? 0 : itemIndex) + 1)
                .append(" 条");
        appendCaseTitle(message, item == null ? null : item.title());
        message.append(" 已加入列表");
        String angle = blankToNull(item.testAngle());
        String reason = blankToNull(item.generationReason());
        String evidence = blankToNull(item.requirementEvidence());
        if (angle != null) {
            message.append("｜角度：").append(cleanInternalCaseIndexText(angle));
        }
        if (evidence != null) {
            message.append("｜依据：").append(cleanInternalCaseIndexText(evidence));
        }
        if (reason != null) {
            message.append("｜原因：").append(cleanInternalCaseIndexText(reason));
        } else if (blankToNull(item.riskNotes()) != null) {
            message.append("｜关注：").append(cleanInternalCaseIndexText(item.riskNotes()));
        }
        return message.toString();
    }

    String buildReviewedCaseEventMessage(Integer itemIndex, String itemTitle, String status, String summary, String coverageComment, String evidenceComment) {
        StringBuilder message = new StringBuilder("第 ")
                .append((itemIndex == null ? 0 : itemIndex) + 1)
                .append(" 条");
        appendCaseTitle(message, itemTitle);
        message.append(" 评审")
                .append(reviewStatusLabel(status));
        String normalizedSummary = blankToNull(summary);
        if (normalizedSummary != null) {
            message.append("｜理由：").append(cleanInternalCaseIndexText(normalizedSummary));
        }
        String normalizedCoverage = blankToNull(coverageComment);
        if (normalizedCoverage != null && !normalizedCoverage.equals(normalizedSummary)) {
            message.append("｜覆盖：").append(cleanInternalCaseIndexText(normalizedCoverage));
        }
        String normalizedEvidence = blankToNull(evidenceComment);
        if (normalizedEvidence != null) {
            message.append("｜依据评价：").append(cleanInternalCaseIndexText(normalizedEvidence));
        }
        return message.toString();
    }

    String buildSupplementedCaseEventMessage(Integer itemIndex, GeneratedAiCaseItem item) {
        StringBuilder message = new StringBuilder("第 ")
                .append((itemIndex == null ? 0 : itemIndex) + 1)
                .append(" 条");
        appendCaseTitle(message, item == null ? null : item.title());
        message.append(" 由评审补充");
        String reason = firstNonBlank(item.supplementReason(), item.coverageGap(), item.generationReason());
        if (reason != null) {
            message.append("｜原因：").append(cleanInternalCaseIndexText(reason));
        }
        return message.toString();
    }

    private void appendCaseTitle(StringBuilder message, String title) {
        String normalized = blankToNull(title);
        if (normalized != null) {
            message.append("：").append(normalized);
        } else {
            message.append("用例");
        }
    }

    private String cleanInternalCaseIndexText(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return "";
        }
        Matcher matcher = INTERNAL_CASE_INDEX_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            int displayIndex;
            try {
                displayIndex = Integer.parseInt(matcher.group(1)) + 1;
            } catch (NumberFormatException ignored) {
                displayIndex = 1;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("第 " + displayIndex + " 条"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
