package com.company.autoplatform.apiautomation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApiAutomationModels {

    private ApiAutomationModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiKeyValueInput(
            String key,
            String value,
            String description,
            Boolean enabled,
            String paramType,
            Boolean required,
            Boolean encode,
            Integer minLength,
            Integer maxLength,
            String fileName,
            String contentType,
            String fileBase64
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAuthCredentialInput(
            String userName,
            String password
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAuthConfigInput(
            String authType,
            ApiAuthCredentialInput basicAuth,
            ApiAuthCredentialInput digestAuth
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiRequestBodyInput(
            String type,
            String rawText,
            List<ApiKeyValueInput> formItems,
            String contentType,
            String fileName,
            String binaryBase64
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAssertionItemInput(
            String header,
            String expression,
            String variableName,
            String condition,
            String expectedValue,
            Boolean enabled
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAssertionGroupInput(
            List<ApiAssertionItemInput> assertions,
            String responseFormat
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiAssertionInput(
            String type,
            String subject,
            String operator,
            String expectedValue,
            String id,
            String assertionType,
            String name,
            Boolean enabled,
            String description,
            String condition,
            List<ApiAssertionItemInput> assertions,
            String assertionBodyType,
            ApiAssertionGroupInput jsonPathAssertion,
            ApiAssertionGroupInput xpathAssertion,
            ApiAssertionGroupInput regexAssertion,
            List<ApiAssertionItemInput> variableAssertionItems,
            String scriptLanguage,
            String script
    ) {
        public ApiAssertionInput(String type, String subject, String operator, String expectedValue) {
            this(type, subject, operator, expectedValue, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiExtractorInput(
            String name,
            String sourceType,
            String expression
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiProcessorExtractItemInput(
            String name,
            String sourceType,
            String expression,
            Boolean enabled,
            String variableName,
            String description,
            String variableType,
            String extractType,
            String extractScope,
            String expressionMatchingRule,
            String resultMatchingRule,
            Integer resultMatchingRuleNum,
            String responseFormat
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiProcessorInput(
            String id,
            String processorType,
            String name,
            Boolean enabled,
            String description,
            String scriptLanguage,
            String script,
            Integer delayMs,
            Long dataSourceId,
            String dataSourceName,
            Integer queryTimeout,
            String variableNames,
            List<ApiKeyValueInput> extractParams,
            String resultVariable,
            List<ApiProcessorExtractItemInput> extractors
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiRequestConfigInput(
            @NotBlank(message = "HTTP method cannot be blank") String method,
            @NotBlank(message = "Path cannot be blank") String path,
            Integer timeoutMs,
            List<ApiKeyValueInput> queryParams,
            List<ApiKeyValueInput> headers,
            List<ApiKeyValueInput> cookies,
            ApiRequestBodyInput body,
            ApiAuthConfigInput authConfig
    ) {
    }

    public record SaveApiDefinitionRequest(
            String workspaceCode,
            @NotBlank(message = "Definition name cannot be blank") String name,
            String directoryName,
            String description,
            List<String> tags,
            @Valid @NotNull(message = "Request config cannot be blank") ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiExtractorInput> extractors,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors
    ) {
    }

    public record ApiDefinitionItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String name,
            String method,
            String path,
            String directoryName,
            String description,
            List<String> tags,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ApiDefinitionDetail(
            Long id,
            String workspaceCode,
            String workspaceName,
            String name,
            String method,
            String path,
            String directoryName,
            String description,
            List<String> tags,
            ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiExtractorInput> extractors,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ApiDefinitionModuleRequest(
            String workspaceCode,
            Long parentId,
            @NotBlank(message = "Module name cannot be blank") String name
    ) {
    }

    public record MoveApiDefinitionModuleRequest(
            Long parentId,
            Integer sortOrder
    ) {
    }

    public record ApiDefinitionModuleItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long parentId,
            String name,
            String fullPath,
            Integer sortOrder,
            Long definitionCount,
            List<ApiDefinitionModuleItem> children
    ) {
    }

    public record SaveApiDefinitionCaseRequest(
            String workspaceCode,
            @NotNull(message = "Definition id cannot be blank") Long definitionId,
            @NotBlank(message = "Case name cannot be blank") String name,
            String description,
            List<String> tags,
            @Valid @NotNull(message = "Request config cannot be blank") ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors
    ) {
    }

    public record ApiDefinitionCaseItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long definitionId,
            String definitionName,
            String name,
            String method,
            String path,
            String description,
            List<String> tags,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ApiDefinitionCaseDetail(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long definitionId,
            String definitionName,
            String name,
            String method,
            String path,
            String description,
            List<String> tags,
            ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiExtractorInput> extractors,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ApiDefinitionCaseRunHistoryItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long caseId,
            Long definitionId,
            String caseName,
            Long reportId,
            String result,
            String failureSummary,
            Integer statusCode,
            Long durationMs,
            Long responseSize,
            Long environmentId,
            String environmentName,
            Long variableSetId,
            String variableSetName,
            String operator,
            LocalDateTime createdAt
    ) {
    }

    public record ApiDefinitionCaseRunHistoryDetail(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long caseId,
            Long definitionId,
            String caseName,
            Long reportId,
            String result,
            String failureSummary,
            Integer statusCode,
            Long durationMs,
            Long responseSize,
            Long environmentId,
            String environmentName,
            Long variableSetId,
            String variableSetName,
            String operator,
            LocalDateTime createdAt,
            List<ApiRunStepResultResponse> stepResults
    ) {
    }

    public record ApiDefinitionCaseChangeHistoryItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long caseId,
            Long definitionId,
            String caseName,
            String changeType,
            String changeSummary,
            Long operatorId,
            String operatorName,
            LocalDateTime createdAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiScenarioStepInput(
            String id,
            String stepName,
            String stepType,
            String resourceType,
            Long resourceId,
            Boolean enabled,
            ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors,
            Integer delayMs,
            String conditionType,
            String conditionExpression,
            String loopType,
            Integer loopCount,
            String foreachExpression,
            String script,
            List<@Valid ApiScenarioStepInput> children
    ) {
        public ApiScenarioStepInput(String stepName, String resourceType, Long resourceId, Boolean enabled) {
            this(null, stepName, null, resourceType, resourceId, enabled, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiScenarioAssertionInput(
            String id,
            String name,
            String assertionType,
            String operator,
            String expectedValue,
            Boolean enabled
    ) {
    }

    public record ApiScenarioModuleRequest(
            String workspaceCode,
            Long parentId,
            @NotBlank(message = "Module name cannot be blank") String name
    ) {
    }

    public record MoveApiScenarioModuleRequest(
            Long parentId,
            Integer sortOrder
    ) {
    }

    public record ApiScenarioModuleItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long parentId,
            String name,
            Integer sortOrder,
            Long scenarioCount,
            List<ApiScenarioModuleItem> children
    ) {
    }

    public record SaveApiScenarioRequest(
            String workspaceCode,
            @NotBlank(message = "Scenario name cannot be blank") String name,
            String directoryName,
            Long moduleId,
            String priority,
            String status,
            String description,
            List<String> tags,
            Long defaultEnvironmentId,
            Long variableSetId,
            Boolean continueOnFailure,
            Long relatedCaseId,
            List<ApiVariableItem> scenarioVariables,
            List<ApiScenarioAssertionInput> scenarioAssertions,
            List<@Valid ApiScenarioStepInput> steps
    ) {
    }

    public record ApiScenarioItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String name,
            String directoryName,
            Long moduleId,
            String moduleName,
            String priority,
            String status,
            String description,
            List<String> tags,
            Integer stepCount,
            Long defaultEnvironmentId,
            Long variableSetId,
            Boolean continueOnFailure,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ApiScenarioDetail(
            Long id,
            String workspaceCode,
            String workspaceName,
            String name,
            String directoryName,
            Long moduleId,
            String moduleName,
            String priority,
            String status,
            String description,
            List<String> tags,
            Long defaultEnvironmentId,
            Long variableSetId,
            Boolean continueOnFailure,
            Long relatedCaseId,
            List<ApiVariableItem> scenarioVariables,
            List<ApiScenarioAssertionInput> scenarioAssertions,
            List<ApiScenarioStepInput> steps,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ApiEnvironmentRequest(
            String workspaceCode,
            @NotBlank(message = "Environment name cannot be blank") String name,
            @NotBlank(message = "Base URL cannot be blank") String baseUrl,
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig,
            Integer timeoutMs,
            Integer status
    ) {
    }

    public record ApiEnvironmentItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String name,
            String baseUrl,
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig,
            Integer timeoutMs,
            Integer status
    ) {
    }

    public record ApiVariableItem(
            String name,
            String value,
            Boolean sensitive
    ) {
    }

    public record ApiVariableSetRequest(
            String workspaceCode,
            @NotBlank(message = "Variable set name cannot be blank") String name,
            List<ApiVariableItem> variables,
            Integer status
    ) {
    }

    public record ApiVariableSetItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String name,
            List<ApiVariableItem> variables,
            Integer status
    ) {
    }

    public record ApiRunRequest(
            String workspaceCode,
            Long environmentId,
            Long variableSetId
    ) {
    }

    public record ApiDebugDefinitionRequest(
            String workspaceCode,
            Long definitionId,
            String name,
            @Valid @NotNull(message = "Request config cannot be blank") ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiExtractorInput> extractors,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors,
            Long environmentId,
            Long variableSetId
    ) {
    }

    public record ApiDebugCaseRequest(
            String workspaceCode,
            Long caseId,
            Long definitionId,
            String name,
            @Valid @NotNull(message = "Request config cannot be blank") ApiRequestConfigInput requestConfig,
            List<ApiAssertionInput> assertions,
            List<ApiProcessorInput> preProcessors,
            List<ApiProcessorInput> postProcessors,
            Long environmentId,
            Long variableSetId
    ) {
    }

    public record ApiRequestSnapshot(
            String method,
            String url,
            Map<String, String> headers,
            List<ApiKeyValueInput> queryParams,
            List<ApiKeyValueInput> cookies,
            String bodyType,
            String bodyContentType,
            List<ApiKeyValueInput> bodyFormItems,
            String bodyFileName,
            String bodyFileContentType,
            String body
    ) {
    }

    public record ApiResponseSnapshot(
            Integer statusCode,
            Map<String, String> headers,
            String body,
            String contentType
    ) {
    }

    public record ApiAssertionResult(
            String id,
            String type,
            String name,
            String subject,
            String condition,
            String expectedValue,
            String actualValue,
            boolean success,
            String message
    ) {
        public ApiAssertionResult(String type, String subject, boolean success, String message) {
            this(null, type, null, subject, null, null, null, success, message);
        }
    }

    public record ApiExtractionResult(
            String name,
            boolean success,
            String value,
            String message
    ) {
    }

    public record ApiProcessorResult(
            String stage,
            String processorType,
            String name,
            boolean success,
            Long durationMs,
            String message,
            List<String> logs,
            Map<String, String> outputVariables
    ) {
    }

    public record ApiRunStepResultResponse(
            Long id,
            Long reportId,
            Integer stepOrder,
            String stepName,
            Long definitionId,
            boolean success,
            Long durationMs,
            ApiRequestSnapshot request,
            ApiResponseSnapshot response,
            List<ApiAssertionResult> assertionResults,
            List<ApiExtractionResult> extractionResults,
            List<ApiProcessorResult> processorResults,
            String errorMessage,
            LocalDateTime createdAt
    ) {
    }

    public record ApiRunResponse(
            Long taskId,
            Long reportId,
            String taskName,
            String reportName,
            String result,
            String failureSummary,
            List<ApiRunStepResultResponse> stepResults
    ) {
    }
}
