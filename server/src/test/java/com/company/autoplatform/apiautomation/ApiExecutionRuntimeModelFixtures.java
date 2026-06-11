package com.company.autoplatform.apiautomation;

import java.util.ArrayList;
import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

final class ApiExecutionRuntimeModelFixtures {

    private ApiExecutionRuntimeModelFixtures() {
    }

    static ApiExecutionRuntimeModels.ResolvedEnvironment resolvedEnvironment(
            String baseUrl,
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig
    ) {
        return new ApiExecutionRuntimeModels.ResolvedEnvironment(null, baseUrl, headers, authConfig, 1000, List.of());
    }

    static ApiExecutionRuntimeModels.MutableRequestConfig mutableRequestConfig(
            ApiRequestBodyInput body,
            ApiAuthConfigInput authConfig
    ) {
        return new ApiExecutionRuntimeModels.MutableRequestConfig(
                "GET",
                "/api",
                1000,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                body,
                authConfig
        );
    }

    static ApiExecutionRuntimeModels.RunStepComputation runStepComputation(
            boolean success,
            ApiRunStepResultResponse response
    ) {
        return new ApiExecutionRuntimeModels.RunStepComputation(success, response);
    }
}
