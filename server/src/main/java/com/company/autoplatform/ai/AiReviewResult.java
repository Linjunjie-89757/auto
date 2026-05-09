package com.company.autoplatform.ai;

import java.util.List;

public record AiReviewResult(
        String result,
        String summary,
        List<String> issues,
        List<String> suggestions,
        String rawContent,
        boolean structured
) {
}
