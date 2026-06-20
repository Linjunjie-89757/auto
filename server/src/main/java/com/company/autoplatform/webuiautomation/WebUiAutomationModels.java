package com.company.autoplatform.webuiautomation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WebUiAutomationModels {

    private WebUiAutomationModels() {
    }

    public record SaveWebUiCaseRequest(
            String workspaceCode,
            String moduleName,
            @NotBlank(message = "Case name cannot be blank") String caseName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            List<@Valid SaveWebUiCaseStepRequest> steps
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SaveWebUiCaseStepRequest(
            @NotBlank(message = "Step name cannot be blank") String stepName,
            String stepType,
            String locatorType,
            String locatorValue,
            String inputValue,
            Integer timeoutMs,
            Boolean continueOnFailure,
            String screenshotPolicy,
            Boolean enabled,
            Integer sortOrder
    ) {
    }

    public record WebUiCaseItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String moduleName,
            String caseName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            Integer stepCount,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime updatedAt
    ) {
    }

    public record WebUiCaseDetail(
            Long id,
            String workspaceCode,
            String workspaceName,
            String moduleName,
            String caseName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            String lastRunResult,
            LocalDateTime lastRunAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<WebUiCaseStepItem> steps
    ) {
    }

    public record WebUiCaseStepItem(
            Long id,
            Long caseId,
            String stepName,
            String stepType,
            String locatorType,
            String locatorValue,
            String inputValue,
            Integer timeoutMs,
            Boolean continueOnFailure,
            String screenshotPolicy,
            Boolean enabled,
            Integer sortOrder
    ) {
    }

    public record SaveWebUiCaseTemplateRequest(
            String workspaceCode,
            String moduleName,
            @NotBlank(message = "Template name cannot be blank") String templateName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            List<@Valid SaveWebUiCaseStepRequest> steps
    ) {
    }

    public record SaveWebUiTemplateFromCaseRequest(
            String workspaceCode,
            @NotBlank(message = "Template name cannot be blank") String templateName,
            String description
    ) {
    }

    public record WebUiCaseTemplateItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String moduleName,
            String templateName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            Integer stepCount,
            LocalDateTime updatedAt
    ) {
    }

    public record WebUiCaseTemplateDetail(
            Long id,
            String workspaceCode,
            String workspaceName,
            String moduleName,
            String templateName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            Integer stepCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<WebUiCaseTemplateStepItem> steps
    ) {
    }

    public record WebUiCaseTemplateStepItem(
            Long id,
            Long templateId,
            String stepName,
            String stepType,
            String locatorType,
            String locatorValue,
            String inputValue,
            Integer timeoutMs,
            Boolean continueOnFailure,
            String screenshotPolicy,
            Boolean enabled,
            Integer sortOrder
    ) {
    }

    public record SaveWebUiEnvironmentRequest(
            String workspaceCode,
            @NotBlank(message = "Environment name cannot be blank") String environmentName,
            @NotBlank(message = "Base URL cannot be blank") String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            Integer status
    ) {
    }

    public record WebUiEnvironmentItem(
            Long id,
            String workspaceCode,
            String workspaceName,
            String environmentName,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            Integer status,
            String source,
            Long defaultVariableSetId,
            String defaultVariableSetName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record WebUiRunRequest(
            Long environmentId,
            Boolean headless,
            Long variableSetId,
            Map<String, String> runtimeVariables
    ) {
    }

    public record WebUiBatchRunRequest(
            String batchName,
            List<Long> caseIds,
            Long environmentId,
            Boolean headless,
            Boolean stopOnFailure,
            Long variableSetId,
            Map<String, String> runtimeVariables
    ) {
    }

    public record WebUiCiBatchRunRequest(
            @NotBlank(message = "Workspace code cannot be blank") String workspaceCode,
            String batchName,
            List<Long> caseIds,
            Long environmentId,
            Boolean headless,
            Boolean stopOnFailure,
            String externalBuildId,
            Long variableSetId,
            Map<String, String> runtimeVariables
    ) {
    }

    public record SaveWebUiCiTokenRequest(
            String workspaceCode,
            @NotBlank(message = "Token name cannot be blank") String tokenName
    ) {
    }

    public record DebugRunWebUiCaseRequest(
            Long caseId,
            Long environmentId,
            String workspaceCode,
            String moduleName,
            @NotBlank(message = "Case name cannot be blank") String caseName,
            String description,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            String status,
            Long variableSetId,
            Map<String, String> runtimeVariables,
            List<@Valid SaveWebUiCaseStepRequest> steps
    ) {
    }

    public record ValidateWebUiLocatorRequest(
            @NotBlank(message = "Base URL cannot be blank") String baseUrl,
            String browserType,
            Boolean headless,
            @NotBlank(message = "Locator type cannot be blank") String locatorType,
            @NotBlank(message = "Locator value cannot be blank") String locatorValue,
            Integer timeoutMs
    ) {
    }

    public record ValidateWebUiLocatorResponse(
            Boolean matched,
            Integer matchCount,
            String errorMessage,
            String screenshotBase64
    ) {
    }

    public record WebUiRunSummary(
            Long id,
            String workspaceCode,
            String workspaceName,
            Long batchId,
            Integer batchSortOrder,
            Long caseId,
            String caseName,
            Long environmentId,
            String environmentName,
            String status,
            String browserType,
            Boolean headless,
            String baseUrl,
            Long durationMs,
            String failureSummary,
            Integer totalSteps,
            Integer passedSteps,
            Integer failedSteps,
            Integer skippedSteps,
            String operatorName,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt
    ) {
    }

    public record WebUiRunStepResult(
            Long id,
            Long caseStepId,
            String stepName,
            String stepType,
            String status,
            String locatorType,
            String locatorValue,
            String inputValueSnapshot,
            Long durationMs,
            String errorMessage,
            Long screenshotArtifactId,
            String screenshotUrl,
            Integer sortOrder,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }

    public record WebUiRunDetail(
            WebUiRunSummary summary,
            WebUiExecutionContextSupport.ExecutionContextSnapshot context,
            List<WebUiRunStepResult> steps
    ) {
    }

    public record WebUiRunResponse(
            Long runId,
            Long batchId,
            Long caseId,
            String caseName,
            String status,
            Long durationMs,
            String failureSummary,
            Integer totalSteps,
            Integer passedSteps,
            Integer failedSteps,
            Integer skippedSteps,
            List<WebUiRunStepResult> stepResults
    ) {
    }

    public record WebUiRunBatchSummary(
            Long id,
            String workspaceCode,
            String workspaceName,
            String batchName,
            String source,
            Long environmentId,
            String environmentName,
            String status,
            Integer totalCases,
            Integer successCases,
            Integer failedCases,
            Long durationMs,
            String failureSummary,
            String operatorName,
            Long ciTokenId,
            String externalBuildId,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt
    ) {
    }

    public record WebUiRunBatchDetail(
            WebUiRunBatchSummary summary,
            List<WebUiRunSummary> runs
    ) {
    }

    public record WebUiBatchRunResponse(
            Long batchId,
            String batchName,
            String status,
            Integer totalCases,
            Integer successCases,
            Integer failedCases,
            Long durationMs,
            String failureSummary,
            List<WebUiRunSummary> runs
    ) {
    }

    public record WebUiCiBatchRunResponse(
            Long batchId,
            String batchName,
            String status,
            Boolean passed,
            Integer totalCases,
            Integer successCases,
            Integer failedCases,
            Long durationMs,
            String failureSummary,
            String externalBuildId,
            String reportUrl,
            String summaryText,
            List<WebUiCiFailedRunSummary> failedRuns,
            List<WebUiRunSummary> runs
    ) {
    }

    public record WebUiCiFailedRunSummary(
            Long runId,
            Long caseId,
            String caseName,
            String status,
            String failureSummary,
            String reportUrl
    ) {
    }

    public record WebUiCiTokenSummary(
            Long id,
            String workspaceCode,
            String workspaceName,
            String tokenName,
            Integer status,
            String createdBy,
            LocalDateTime lastUsedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record WebUiCiTokenCreated(
            Long id,
            String workspaceCode,
            String workspaceName,
            String tokenName,
            Integer status,
            String createdBy,
            LocalDateTime lastUsedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String token
    ) {
    }

    public record SaveWebUiReportShareRequest(
            String shareType,
            Long targetId,
            Integer expiresInDays
    ) {
    }

    public record WebUiReportShareSummary(
            Long id,
            String workspaceCode,
            String workspaceName,
            String shareType,
            Long targetId,
            Integer status,
            LocalDateTime expiresAt,
            String createdBy,
            LocalDateTime lastAccessedAt,
            Integer accessCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record WebUiReportShareCreated(
            Long id,
            String workspaceCode,
            String workspaceName,
            String shareType,
            Long targetId,
            Integer status,
            LocalDateTime expiresAt,
            String createdBy,
            LocalDateTime lastAccessedAt,
            Integer accessCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String token,
            String shareUrl
    ) {
    }

    public record WebUiSharedReport(
            String shareType,
            WebUiRunDetail run,
            WebUiRunBatchDetail batch,
            LocalDateTime expiresAt,
            LocalDateTime generatedAt
    ) {
    }
}
