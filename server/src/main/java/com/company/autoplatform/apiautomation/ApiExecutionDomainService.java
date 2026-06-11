package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.*;

@Service
public class ApiExecutionDomainService {

    private final ApiExecutionEngineSupport executionEngine;
    private final ApiDefinitionCaseMapper caseMapper;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;

    public ApiExecutionDomainService(
            ApiExecutionEngineSupport executionEngine,
            ApiDefinitionCaseMapper caseMapper,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport
    ) {
        this.executionEngine = executionEngine;
        this.caseMapper = caseMapper;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
    }

    public ApiRunResponse debugRunDefinition(Long id, String workspaceCode, ApiRunRequest request) {
        ApiDefinitionEntity definition = executionEngine.requireDefinition(id);
        workspaceScopeSupport.validateReadable(definition.getWorkspaceId(), workspaceCode, "Current workspace cannot run the definition");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(definition.getWorkspaceId()).getWorkspaceCode());

        ApiExecutionRuntimeModels.ExecutionContext context = executionEngine.buildExecutionContext(definition.getWorkspaceId(), request.environmentId(), request.variableSetId());
        ApiExecutionRuntimeModels.RunEnvelope envelope = executionEngine.createRunEnvelope(definition.getWorkspaceId(), "API", "接口调试", definition.getDefinitionName());
        ApiExecutionRuntimeModels.RunStepComputation step = executionEngine.executeDefinition(definition, definition.getDefinitionName(), 1, context.variables(), context.environment());
        executionEngine.persistStep(envelope.report(), definition.getWorkspaceId(), step);
        executionEngine.finalizeRunDefinition(definition, step.success(), envelope.task(), envelope.report(), step);
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                List.of(step.response())
        );
    }

    public ApiRunResponse runCase(Long id, String workspaceCode, ApiRunRequest request) {
        ApiDefinitionCaseEntity apiCase = executionEngine.requireCase(id);
        workspaceScopeSupport.validateReadable(apiCase.getWorkspaceId(), workspaceCode, "Current workspace cannot run the case");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(apiCase.getWorkspaceId()).getWorkspaceCode());

        ApiExecutionRuntimeModels.ExecutionContext context = executionEngine.buildExecutionContext(apiCase.getWorkspaceId(), request.environmentId(), request.variableSetId());
        ApiExecutionRuntimeModels.RunEnvelope envelope = executionEngine.createRunEnvelope(apiCase.getWorkspaceId(), "API", "接口用例调试", apiCase.getCaseName());
        ApiExecutionRuntimeModels.RunStepComputation step = executionEngine.executeCase(apiCase, apiCase.getCaseName(), 1, context.variables(), context.environment());
        executionEngine.persistStep(envelope.report(), apiCase.getWorkspaceId(), step);
        executionEngine.finalizeRunCase(apiCase, step.success(), envelope.task(), envelope.report(), step);
        executionEngine.persistCaseRunHistory(
                apiCase,
                envelope.report(),
                step,
                request.environmentId(),
                request.variableSetId()
        );
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                List.of(step.response())
        );
    }

    public ApiRunResponse debugRunDefinitionDraft(String workspaceCode, ApiDebugDefinitionRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWorkspace(
                blankToFallback(request.workspaceCode(), workspaceCode)
        );
        workspaceScopeSupport.validateReadable(workspace.getId(), workspaceCode, "Current workspace cannot run the definition");
        workspaceService.requireWritableWorkspace(workspace.getWorkspaceCode());

        if (request.definitionId() != null) {
            ApiDefinitionEntity definition = executionEngine.requireDefinition(request.definitionId());
            if (!definition.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Definition does not belong to the selected workspace");
            }
        }

        ApiRequestConfigInput config = request.requestConfig();
        String method = Optional.ofNullable(config.method()).orElse("").trim().toUpperCase();
        String path = Optional.ofNullable(config.path()).orElse("").trim();
        if (method.isEmpty()) {
            throw new BadRequestException("HTTP method cannot be blank");
        }
        if (path.isEmpty()) {
            throw new BadRequestException("Path cannot be blank");
        }

        ApiDefinitionEntity draftDefinition = new ApiDefinitionEntity();
        draftDefinition.setId(request.definitionId());
        draftDefinition.setWorkspaceId(workspace.getId());
        draftDefinition.setDefinitionName(blankToFallback(request.name(), method + " " + path));
        draftDefinition.setHttpMethod(method);
        draftDefinition.setPath(path);
        draftDefinition.setRequestJson(ApiAutomationJsonSupport.toJson(config, "Failed to serialize request config"));
        draftDefinition.setAssertionsJson(ApiAutomationJsonSupport.toJson(defaultList(request.assertions()), "Failed to serialize assertions"));
        draftDefinition.setExtractorsJson(ApiAutomationJsonSupport.toJson(defaultList(request.extractors()), "Failed to serialize extractors"));
        draftDefinition.setPreprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(request.preProcessors(), "PRE"),
                "Failed to serialize pre-processors"));
        draftDefinition.setPostprocessorsJson(ApiAutomationJsonSupport.toJson(normalizePostProcessors(request.postProcessors(), request.extractors()),
                "Failed to serialize post-processors"));

        ApiExecutionRuntimeModels.ExecutionContext context = executionEngine.buildExecutionContext(workspace.getId(), request.environmentId(), request.variableSetId());
        ApiExecutionRuntimeModels.RunEnvelope envelope = executionEngine.createRunEnvelope(workspace.getId(), "API", "接口调试", draftDefinition.getDefinitionName());
        ApiExecutionRuntimeModels.RunStepComputation step = executionEngine.executeDefinition(draftDefinition, draftDefinition.getDefinitionName(), 1, context.variables(), context.environment());
        executionEngine.persistStep(envelope.report(), workspace.getId(), step);
        executionEngine.finalizeRunTaskAndReport(
                envelope.task(),
                envelope.report(),
                step.success() ? "SUCCESS" : "FAILED",
                step.response().errorMessage()
        );
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                List.of(step.response())
        );
    }

    public ApiRunResponse debugRunCaseDraft(String workspaceCode, ApiDebugCaseRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWorkspace(
                blankToFallback(request.workspaceCode(), workspaceCode)
        );
        workspaceScopeSupport.validateReadable(workspace.getId(), workspaceCode, "Current workspace cannot run the case");
        workspaceService.requireWritableWorkspace(workspace.getWorkspaceCode());

        ApiDefinitionCaseEntity persistedCase = null;
        if (request.caseId() != null) {
            persistedCase = executionEngine.requireCase(request.caseId());
            if (!persistedCase.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Case does not belong to the selected workspace");
            }
        }
        if (request.definitionId() == null) {
            throw new BadRequestException("Definition id cannot be blank");
        }
        ApiDefinitionEntity definition = executionEngine.requireDefinition(request.definitionId());
        if (!definition.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Definition does not belong to the selected workspace");
        }

        ApiRequestConfigInput config = request.requestConfig();
        String method = Optional.ofNullable(config.method()).orElse("").trim().toUpperCase();
        String path = Optional.ofNullable(config.path()).orElse("").trim();
        if (method.isEmpty() || path.isEmpty()) {
            throw new BadRequestException("Method and path cannot be blank");
        }

        ApiDefinitionCaseEntity draftCase = new ApiDefinitionCaseEntity();
        draftCase.setWorkspaceId(workspace.getId());
        draftCase.setDefinitionId(definition.getId());
        draftCase.setCaseName(blankToFallback(request.name(), "未命名用例"));
        draftCase.setRequestJson(ApiAutomationJsonSupport.toJson(request.requestConfig(), "Failed to serialize case request"));
        draftCase.setAssertionsJson(ApiAutomationJsonSupport.toJson(defaultList(request.assertions()), "Failed to serialize case assertions"));
        draftCase.setPreprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(request.preProcessors(), "PRE"),
                "Failed to serialize case preprocessors"));
        draftCase.setPostprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(request.postProcessors(), "POST"),
                "Failed to serialize case postprocessors"));

        ApiExecutionRuntimeModels.ExecutionContext context = executionEngine.buildExecutionContext(workspace.getId(), request.environmentId(), request.variableSetId());
        ApiExecutionRuntimeModels.RunEnvelope envelope = executionEngine.createRunEnvelope(workspace.getId(), "API", "接口用例调试", draftCase.getCaseName());
        ApiExecutionRuntimeModels.RunStepComputation step = executionEngine.executeCase(draftCase, draftCase.getCaseName(), 1, context.variables(), context.environment());
        executionEngine.persistStep(envelope.report(), workspace.getId(), step);
        executionEngine.finalizeRunTaskAndReport(
                envelope.task(),
                envelope.report(),
                step.success() ? "SUCCESS" : "FAILED",
                step.response().errorMessage()
        );
        if (persistedCase != null) {
            persistedCase.setLastRunResult(step.success() ? "SUCCESS" : "FAILED");
            persistedCase.setLastRunAt(LocalDateTime.now());
            persistedCase.setUpdatedAt(LocalDateTime.now());
            caseMapper.updateById(persistedCase);
            executionEngine.persistCaseRunHistory(
                    persistedCase,
                    envelope.report(),
                    step,
                    request.environmentId(),
                    request.variableSetId()
            );
        }
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                List.of(step.response())
        );
    }

    public ApiRunResponse runScenario(Long id, String workspaceCode, ApiRunRequest request) {
        ApiScenarioEntity scenario = executionEngine.requireScenario(id);
        workspaceScopeSupport.validateReadable(scenario.getWorkspaceId(), workspaceCode, "Current workspace cannot run the scenario");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(scenario.getWorkspaceId()).getWorkspaceCode());

        Long environmentId = request.environmentId() != null ? request.environmentId() : scenario.getDefaultEnvId();
        Long variableSetId = request.variableSetId() != null ? request.variableSetId() : scenario.getVariableSetId();
        ApiExecutionRuntimeModels.ExecutionContext context = executionEngine.buildExecutionContext(scenario.getWorkspaceId(), environmentId, variableSetId);
        for (ApiVariableItem variable : readVariables(scenario.getScenarioVariablesJson())) {
            if (variable.name() != null && !variable.name().isBlank()) {
                context.variables().put(variable.name().trim(), Optional.ofNullable(variable.value()).orElse(""));
            }
        }
        ApiExecutionRuntimeModels.RunEnvelope envelope = executionEngine.createRunEnvelope(scenario.getWorkspaceId(), "API", "接口场景", scenario.getScenarioName());
        List<ApiScenarioStepInput> steps = readScenarioSteps(scenario.getStepsJson());
        List<ApiRunStepResultResponse> responses = new ArrayList<>();
        boolean success = true;
        String failureSummary = null;
        int[] stepOrder = {1};
        Set<String> onceOnlyKeys = new java.util.HashSet<>();
        for (ApiExecutionRuntimeModels.RunStepComputation computation : executionEngine.executeScenarioSteps(
                steps,
                stepOrder,
                context.variables(),
                context.environment(),
                workspaceService.requireWorkspaceById(scenario.getWorkspaceId()).getWorkspaceCode(),
                scenario.getWorkspaceId(),
                scenario.getId(),
                0,
                onceOnlyKeys,
                Boolean.TRUE.equals(scenario.getContinueOnFailure())
        )) {
            executionEngine.persistStep(envelope.report(), scenario.getWorkspaceId(), computation);
            responses.add(computation.response());
            if (!computation.success()) {
                success = false;
                failureSummary = blankToFallback(computation.response().errorMessage(), computation.response().stepName() + " failed");
                if (!Boolean.TRUE.equals(scenario.getContinueOnFailure())) {
                    break;
                }
            }
        }

        if (responses.isEmpty()) {
            throw new BadRequestException("Scenario has no enabled steps to run");
        }

        List<ApiAssertionResult> scenarioAssertionResults = executionEngine.evaluateScenarioAssertions(readScenarioAssertions(scenario.getScenarioAssertionsJson()), responses);
        if (!scenarioAssertionResults.isEmpty()) {
            boolean assertionSuccess = scenarioAssertionResults.stream().allMatch(ApiAssertionResult::success);
            ApiExecutionRuntimeModels.RunStepComputation assertionComputation = new ApiExecutionRuntimeModels.RunStepComputation(assertionSuccess, new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder[0],
                    "场景断言",
                    null,
                    assertionSuccess,
                    0L,
                    null,
                    null,
                    scenarioAssertionResults,
                    List.of(),
                    List.of(),
                    assertionSuccess ? null : executionEngine.firstFailedMessage(scenarioAssertionResults),
                    LocalDateTime.now()
            ));
            executionEngine.persistStep(envelope.report(), scenario.getWorkspaceId(), assertionComputation);
            responses.add(assertionComputation.response());
            if (!assertionSuccess) {
                success = false;
                failureSummary = executionEngine.firstFailedMessage(scenarioAssertionResults);
            }
        }

        executionEngine.finalizeRunScenario(scenario, success, failureSummary, envelope.task(), envelope.report());
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                responses
        );
    }
}

