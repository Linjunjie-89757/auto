package com.company.autoplatform.ai;

public record TestAiCaseConfigResponse(
        boolean success,
        String provider,
        String model,
        String message
) {
}
