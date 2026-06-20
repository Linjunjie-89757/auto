package com.company.autoplatform.webuiautomation;

import java.util.List;

public interface WebUiBrowserRunner {

    List<StepExecutionResult> run(WebUiRunContext context);

    record WebUiRunContext(
            String browserType,
            boolean headless,
            String baseUrl,
            int defaultTimeoutMs,
            List<WebUiCaseStepEntity> steps
    ) {
    }

    record StepExecutionResult(
            WebUiCaseStepEntity step,
            boolean success,
            long durationMs,
            String errorMessage,
            byte[] screenshotBytes
    ) {
    }
}
