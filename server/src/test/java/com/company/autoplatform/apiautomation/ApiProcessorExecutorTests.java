package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static com.company.autoplatform.apiautomation.ApiExecutionRuntimeModelFixtures.mutableRequestConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiProcessorExecutorTests {

    private final ApiProcessorExecutor processorExecutor = new ApiProcessorExecutor(
            new ApiAutomationScriptRunner(),
            new ApiVariableResolver(),
            null,
            null,
            null
    );

    @Test
    void scriptProcessorWritesVariablesAndRecordsChangedValues() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("token", "old");
        List<ApiProcessorResult> processorResults = new ArrayList<>();

        processorExecutor.executeProcessors(
                "PRE",
                List.of(new ApiProcessorInput(
                        "script-1",
                        "SCRIPT",
                        "Script writes variables",
                        true,
                        null,
                        "JAVASCRIPT",
                        "setVar('token', 'new'); setVar('extra', 'created'); log('done');",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        List.of()
                )),
                1L,
                requestConfig(),
                null,
                null,
                null,
                variables,
                processorResults,
                new ArrayList<>()
        );

        assertThat(variables)
                .containsEntry("token", "new")
                .containsEntry("extra", "created");
        assertThat(processorResults).hasSize(1);
        assertThat(processorResults.getFirst().success()).isTrue();
        assertThat(processorResults.getFirst().outputVariables())
                .containsEntry("token", "new")
                .containsEntry("extra", "created");
    }

    @Test
    void extractorFailureIsRecordedBeforeThrowingProcessorFailure() {
        Map<String, String> variables = new LinkedHashMap<>();
        List<ApiProcessorResult> processorResults = new ArrayList<>();
        List<ApiExtractionResult> extractionResults = new ArrayList<>();
        ApiResponseSnapshot response = new ApiResponseSnapshot(200, Map.of(), "{\"name\":\"Alice\"}", "application/json");

        assertThatThrownBy(() -> processorExecutor.executeProcessors(
                "POST",
                List.of(new ApiProcessorInput(
                        "extract-1",
                        "EXTRACT",
                        "Broken extractor",
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(extractor("missingName", "JSON_PATH", "BODY", "$.missing.value"))
                )),
                1L,
                requestConfig(),
                null,
                response,
                null,
                variables,
                processorResults,
                extractionResults
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Broken extractor failed");

        assertThat(extractionResults).hasSize(1);
        assertThat(extractionResults.getFirst().name()).isEqualTo("missingName");
        assertThat(extractionResults.getFirst().success()).isFalse();
        assertThat(extractionResults.getFirst().message()).isNotBlank();
        assertThat(processorResults).hasSize(1);
        assertThat(processorResults.getFirst().success()).isFalse();
    }

    @Test
    void sqlProcessorKeepsMissingVariableFailureBehavior() {
        Map<String, String> variables = new LinkedHashMap<>();
        ApiProcessorInput processor = new ApiProcessorInput(
                "sql-1",
                "SQL",
                "SQL missing variable",
                true,
                null,
                null,
                "SELECT '{{missing}}' AS value",
                null,
                1L,
                null,
                5000,
                "value",
                List.of(),
                "rows",
                List.of()
        );

        assertThatThrownBy(() -> processorExecutor.executeSqlProcessor(processor, 1L, variables))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Missing variable: missing");
    }

    private ApiExecutionRuntimeModels.MutableRequestConfig requestConfig() {
        return mutableRequestConfig(
                new ApiRequestBodyInput("NONE", null, List.of(), null, null, null),
                new ApiAuthConfigInput("NONE", null, null)
        );
    }

    private ApiProcessorExtractItemInput extractor(String variableName, String type, String scope, String expression) {
        return new ApiProcessorExtractItemInput(
                variableName,
                null,
                expression,
                true,
                variableName,
                null,
                "TEMPORARY",
                type,
                scope,
                "EXPRESSION",
                "SPECIFIC",
                1,
                "JSON"
        );
    }
}
