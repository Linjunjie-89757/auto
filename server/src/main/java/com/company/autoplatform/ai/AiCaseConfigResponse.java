package com.company.autoplatform.ai;

public record AiCaseConfigResponse(
        AiCaseConfigItem generatorConfig,
        AiCaseConfigItem reviewerConfig,
        boolean hasLegacyConfig,
        boolean canBootstrapFromLegacy
) {
}
