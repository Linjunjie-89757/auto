package com.company.autoplatform.ai;

public record AiCapabilityOverride(
        Boolean textChat,
        Boolean streamOutput,
        Boolean structuredOutput,
        Boolean imageInput,
        Boolean longContext,
        Boolean stableAvailable
) {
    public boolean hasAnyValue() {
        return textChat != null
                || streamOutput != null
                || structuredOutput != null
                || imageInput != null
                || longContext != null
                || stableAvailable != null;
    }
}
