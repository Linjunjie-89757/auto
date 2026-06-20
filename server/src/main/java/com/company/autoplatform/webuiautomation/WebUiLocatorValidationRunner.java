package com.company.autoplatform.webuiautomation;

public interface WebUiLocatorValidationRunner {

    LocatorValidationResult validate(LocatorValidationContext context);

    record LocatorValidationContext(
            String baseUrl,
            String browserType,
            boolean headless,
            String locatorType,
            String locatorValue,
            int timeoutMs
    ) {
    }

    record LocatorValidationResult(
            boolean matched,
            int matchCount,
            String errorMessage,
            byte[] screenshotBytes
    ) {
    }
}
