package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.execution.TaskEntity;
import com.company.autoplatform.execution.TaskMapper;
import com.company.autoplatform.settings.EnvConfigEntity;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetEntity;
import com.company.autoplatform.settings.ParamSetMapper;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.*;

@Service
public class ApiExecutionEngineSupport {

    private static final String API_ENV_TYPE = "API";
    private static final String RESULT_PASSED = "PASSED";
    private static final String RESULT_NOT_PASSED = "NOT_PASSED";
    private static final String RESULT_NO_ASSERTION = "NO_ASSERTION";
    private static final String RESULT_FAILED = "FAILED";
    private static final String API_VARIABLE_SET_TYPE = "API_VARIABLE_SET";
    private static final String SCENARIO_RESOURCE_TYPE_DEFINITION = "DEFINITION";
    private static final String SCENARIO_RESOURCE_TYPE_CASE = "CASE";
    private static final String SCENARIO_STEP_API = "API";
    private static final String SCENARIO_STEP_API_CASE = "API_CASE";
    private static final String SCENARIO_STEP_CUSTOM_REQUEST = "CUSTOM_REQUEST";
    private static final String SCENARIO_STEP_API_SCENARIO = "API_SCENARIO";
    private static final String SCENARIO_STEP_IF_CONTROLLER = "IF_CONTROLLER";
    private static final String SCENARIO_STEP_LOOP_CONTROLLER = "LOOP_CONTROLLER";
    private static final String SCENARIO_STEP_ONCE_ONLY_CONTROLLER = "ONCE_ONLY_CONTROLLER";
    private static final String SCENARIO_STEP_CONSTANT_TIMER = "CONSTANT_TIMER";
    private static final String SCENARIO_STEP_SCRIPT = "SCRIPT";
    private static final int MAX_SCENARIO_LOOP_COUNT = 50;
    private static final int MAX_SCENARIO_WAIT_MS = 60000;
    private static final Set<String> SUCCESS_RESULTS = Set.of("SUCCESS", "FAILED");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ApiDefinitionMapper definitionMapper;
    private final ApiDefinitionCaseMapper caseMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final ApiDefinitionModuleMapper definitionModuleMapper;
    private final ApiScenarioModuleMapper scenarioModuleMapper;
    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final TaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;
    private final ApiAssertionEvaluator assertionEvaluator;
    private final ApiAssertionSupport assertionSupport;
    private final ApiRequestExecutionSupport requestExecutionSupport;
    private final ApiProcessorExecutor processorExecutor;
    private final ApiScenarioExecutionSupport scenarioExecutionSupport;
    private final ApiRunResultPersistenceSupport runResultPersistenceSupport;

    public ApiExecutionEngineSupport(
            ApiDefinitionMapper definitionMapper,
            ApiDefinitionCaseMapper caseMapper,
            ApiScenarioMapper scenarioMapper,
            ApiDefinitionModuleMapper definitionModuleMapper,
            ApiScenarioModuleMapper scenarioModuleMapper,
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper,
            TaskMapper taskMapper,
            ReportMapper reportMapper,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport,
            ApiAssertionEvaluator assertionEvaluator,
            ApiAssertionSupport assertionSupport,
            ApiRequestExecutionSupport requestExecutionSupport,
            ApiProcessorExecutor processorExecutor,
            ApiScenarioExecutionSupport scenarioExecutionSupport,
            ApiRunResultPersistenceSupport runResultPersistenceSupport
    ) {
        this.definitionMapper = definitionMapper;
        this.caseMapper = caseMapper;
        this.scenarioMapper = scenarioMapper;
        this.definitionModuleMapper = definitionModuleMapper;
        this.scenarioModuleMapper = scenarioModuleMapper;
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
        this.assertionEvaluator = assertionEvaluator;
        this.assertionSupport = assertionSupport;
        this.requestExecutionSupport = requestExecutionSupport;
        this.processorExecutor = processorExecutor;
        this.scenarioExecutionSupport = scenarioExecutionSupport;
        this.runResultPersistenceSupport = runResultPersistenceSupport;
    }
    ExecutionContext buildExecutionContext(Long workspaceId, Long environmentId, Long variableSetId) {
        ResolvedEnvironment environment = resolveEnvironment(workspaceId, environmentId);
        Map<String, String> variables = new LinkedHashMap<>();
        for (ApiVariableItem variable : defaultList(environment.variables())) {
            if (variable.name() != null) {
                variables.put(variable.name(), variable.value() == null ? "" : variable.value());
            }
        }
        if (variableSetId != null) {
            ParamSetEntity variableSet = requireVariableSet(variableSetId);
            if (!variableSet.getWorkspaceId().equals(workspaceId)) {
                throw new BadRequestException("Variable set must belong to the same workspace");
            }
            for (ApiVariableItem variable : readVariables(variableSet.getContentJson())) {
                if (variable.name() != null) {
                    variables.put(variable.name(), variable.value() == null ? "" : variable.value());
                }
            }
        }
        return new ExecutionContext(environment, variables);
    }

    private ResolvedEnvironment resolveEnvironment(Long workspaceId, Long environmentId) {
        if (environmentId == null) {
            return new ResolvedEnvironment(null, "", List.of(), emptyAuthConfig(), 10000, List.of());
        }
        EnvConfigEntity environment = requireEnvironment(environmentId);
        if (!environment.getWorkspaceId().equals(workspaceId)) {
            throw new BadRequestException("Environment must belong to the same workspace");
        }
        EnvironmentConfigPayload config = ApiAutomationJsonSupport.read(environment.getConfigJson(), EnvironmentConfigPayload.class,
                new EnvironmentConfigPayload(List.of(), emptyAuthConfig(), 10000, List.of()));
        return new ResolvedEnvironment(
                environment.getId(),
                environment.getBaseUrl(),
                defaultList(config.headers()),
                normalizeAuth(config.authConfig()),
                config.timeoutMs() == null ? 10000 : config.timeoutMs(),
                defaultList(config.variables())
        );
    }

    RunEnvelope createRunEnvelope(Long workspaceId, String engineType, String prefix, String targetName) {
        LocalDateTime now = LocalDateTime.now();
        TaskEntity task = new TaskEntity();
        task.setWorkspaceId(workspaceId);
        task.setTaskName(prefix + " - " + targetName);
        task.setEngineType(engineType);
        task.setTaskStatus("RUNNING");
        task.setSummary(targetName);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        ReportEntity report = new ReportEntity();
        report.setWorkspaceId(workspaceId);
        report.setTaskId(task.getId());
        report.setReportName(targetName + " @ " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        report.setResult("SUCCESS");
        report.setFailureSummary(null);
        report.setLogSource("API");
        report.setLogText(null);
        report.setAttachmentsJson("[]");
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportMapper.insert(report);
        return new RunEnvelope(task, report);
    }

    RunStepComputation executeDefinition(
            ApiDefinitionEntity definition,
            String stepName,
            int stepOrder,
            Map<String, String> variables,
            ResolvedEnvironment environment
    ) {
        ApiRequestConfigInput config = ApiAutomationJsonSupport.read(definition.getRequestJson(), ApiRequestConfigInput.class,
                new ApiRequestConfigInput(definition.getHttpMethod(), definition.getPath(), 10000, List.of(), List.of(), List.of(),
                        new ApiRequestBodyInput("NONE", null, List.of(), null, null, null), emptyAuthConfig()));
        List<ApiAssertionInput> assertions = readAssertions(definition.getAssertionsJson());
        List<ApiProcessorInput> preProcessors = readPreProcessors(definition);
        List<ApiProcessorInput> postProcessors = readPostProcessors(definition);

        long started = System.currentTimeMillis();
        try {
            MutableRequestConfig requestConfig = toMutableRequestConfig(config);
            List<ApiProcessorResult> processorResults = new ArrayList<>();
            List<ApiExtractionResult> extractionResults = new ArrayList<>();

            processorExecutor.executeProcessors("PRE", preProcessors, definition.getWorkspaceId(), requestConfig, null, null, environment, variables, processorResults, extractionResults);

            ApiRequestConfigInput resolvedConfig = toRequestConfig(requestConfig);
            ApiRequestExecutionSupport.ResolvedRequest request = requestExecutionSupport.resolveRequest(resolvedConfig, environment, variables, normalizeAuth(resolvedConfig.authConfig()));
            ApiRequestExecutionSupport.SentRequestResult sentRequest = requestExecutionSupport.sendRequest(request, resolvedConfig, environment, variables);
            HttpResponse<String> response = sentRequest.response();
            long durationMs = System.currentTimeMillis() - started;

            ApiResponseSnapshot responseSnapshot = new ApiResponseSnapshot(
                    response.statusCode(),
                    requestExecutionSupport.flattenHeaders(response.headers().map()),
                    response.body(),
                    response.headers().firstValue("content-type").orElse(null)
            );
            ApiRequestSnapshot requestSnapshot = new ApiRequestSnapshot(
                    request.method(),
                    request.url(),
                    sentRequest.headers(),
                    defaultList(resolvedConfig.queryParams()),
                    defaultList(resolvedConfig.cookies()),
                    request.bodyConfig() == null ? null : request.bodyConfig().type(),
                    request.bodyConfig() == null ? null : request.bodyConfig().contentType(),
                    request.bodyConfig() == null ? List.of() : defaultList(request.bodyConfig().formItems()),
                    request.bodyConfig() == null ? null : request.bodyConfig().fileName(),
                    request.bodyConfig() == null ? null : request.bodyConfig().contentType(),
                    request.body()
            );
            processorExecutor.executeProcessors("POST", postProcessors, definition.getWorkspaceId(), requestConfig, requestSnapshot, responseSnapshot, environment, variables, processorResults, extractionResults);

            List<ApiAssertionResult> assertionResults = evaluateAssertions(assertions, requestSnapshot, responseSnapshot, durationMs, variables);
            boolean success = assertionResults.stream().allMatch(ApiAssertionResult::success);
            String errorMessage = success ? null : firstFailedMessage(assertionResults);
            ApiRunStepResultResponse result = new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    definition.getId(),
                    success,
                    durationMs,
                    requestSnapshot,
                    responseSnapshot,
                    assertionResults,
                    extractionResults,
                    processorResults,
                    errorMessage,
                    LocalDateTime.now()
            );
            return new RunStepComputation(success, result);
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            long durationMs = System.currentTimeMillis() - started;
            ApiRunStepResultResponse result = new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    definition.getId(),
                    false,
                    durationMs,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    exception.getMessage(),
                    LocalDateTime.now()
            );
            return new RunStepComputation(false, result);
        } catch (RuntimeException exception) {
            long durationMs = System.currentTimeMillis() - started;
            ApiRunStepResultResponse result = new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    definition.getId(),
                    false,
                    durationMs,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    exception.getMessage(),
                    LocalDateTime.now()
            );
            return new RunStepComputation(false, result);
        }
    }

    RunStepComputation executeCase(
            ApiDefinitionCaseEntity apiCase,
            String stepName,
            int stepOrder,
            Map<String, String> variables,
            ResolvedEnvironment environment
    ) {
        ApiDefinitionEntity definition = requireDefinition(apiCase.getDefinitionId());
        ensureDefinitionInWorkspace(definition, apiCase.getWorkspaceId(), "Case definition must belong to the same workspace");
        ApiDefinitionEntity runtimeDefinition = new ApiDefinitionEntity();
        ApiRequestConfigInput requestConfig = readStoredRequestConfig(apiCase.getRequestJson(), definition.getHttpMethod(), definition.getPath());
        runtimeDefinition.setId(definition.getId());
        runtimeDefinition.setWorkspaceId(apiCase.getWorkspaceId());
        runtimeDefinition.setDefinitionName(apiCase.getCaseName());
        runtimeDefinition.setHttpMethod(requestConfig.method());
        runtimeDefinition.setPath(requestConfig.path());
        runtimeDefinition.setRequestJson(apiCase.getRequestJson());
        runtimeDefinition.setAssertionsJson(apiCase.getAssertionsJson());
        runtimeDefinition.setPreprocessorsJson(apiCase.getPreprocessorsJson());
        runtimeDefinition.setPostprocessorsJson(apiCase.getPostprocessorsJson());
        runtimeDefinition.setExtractorsJson("[]");
        return executeDefinition(runtimeDefinition, stepName, stepOrder, variables, environment);
    }

    List<RunStepComputation> executeScenarioSteps(
            List<ApiScenarioStepInput> steps,
            int[] stepOrder,
            Map<String, String> variables,
            ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            Long rootScenarioId,
            int nestingDepth,
            Set<String> onceOnlyKeys,
            boolean continueOnFailure
    ) {
        return scenarioExecutionSupport.executeScenarioSteps(
                steps,
                stepOrder,
                variables,
                environment,
                workspaceCode,
                workspaceId,
                rootScenarioId,
                nestingDepth,
                onceOnlyKeys,
                continueOnFailure,
                new ApiScenarioExecutionSupport.ScenarioExecutionDelegate() {
                    @Override
                    public String normalizeScenarioStepType(ApiScenarioStepInput step) {
                        return ApiExecutionEngineSupport.this.normalizeScenarioStepType(step);
                    }

                    @Override
                    public Long normalizeScenarioResourceId(ApiScenarioStepInput step) {
                        return ApiExecutionEngineSupport.this.normalizeScenarioResourceId(step);
                    }

                    @Override
                    public ApiDefinitionEntity requireDefinition(Long id) {
                        return ApiExecutionEngineSupport.this.requireDefinition(id);
                    }

                    @Override
                    public ApiDefinitionCaseEntity requireCase(Long id) {
                        return ApiExecutionEngineSupport.this.requireCase(id);
                    }

                    @Override
                    public ApiScenarioEntity requireScenario(Long id) {
                        return ApiExecutionEngineSupport.this.requireScenario(id);
                    }

                    @Override
                    public void validateReadable(Long workspaceId, String workspaceCode, String message) {
                        ApiExecutionEngineSupport.this.validateReadable(workspaceId, workspaceCode, message);
                    }

                    @Override
                    public List<ApiScenarioStepInput> readScenarioSteps(String json) {
                        return ApiAutomationFormatSupport.readScenarioSteps(json);
                    }

                    @Override
                    public RunStepComputation executeDefinition(
                            ApiDefinitionEntity definition,
                            String stepName,
                            int stepOrder,
                            Map<String, String> variables,
                            ResolvedEnvironment environment
                    ) {
                        return ApiExecutionEngineSupport.this.executeDefinition(definition, stepName, stepOrder, variables, environment);
                    }

                    @Override
                    public RunStepComputation executeCase(
                            ApiDefinitionCaseEntity apiCase,
                            String stepName,
                            int stepOrder,
                            Map<String, String> variables,
                            ResolvedEnvironment environment
                    ) {
                        return ApiExecutionEngineSupport.this.executeCase(apiCase, stepName, stepOrder, variables, environment);
                    }

                    @Override
                    public RunStepComputation executeCustomRequestStep(
                            ApiScenarioStepInput step,
                            int stepOrder,
                            Map<String, String> variables,
                            ResolvedEnvironment environment,
                            Long workspaceId
                    ) {
                        return ApiExecutionEngineSupport.this.executeCustomRequestStep(step, stepOrder, variables, environment, workspaceId);
                    }
                }
        );
    }

    private RunStepComputation executeCustomRequestStep(
            ApiScenarioStepInput step,
            int stepOrder,
            Map<String, String> variables,
            ResolvedEnvironment environment,
            Long workspaceId
    ) {
        ApiRequestConfigInput requestConfig = step.requestConfig();
        if (requestConfig == null) {
            throw new BadRequestException("Custom request step requires request config");
        }
        ApiDefinitionEntity runtimeDefinition = new ApiDefinitionEntity();
        runtimeDefinition.setId(null);
        runtimeDefinition.setWorkspaceId(workspaceId);
        runtimeDefinition.setDefinitionName(blankToFallback(step.stepName(), requestConfig.method() + " " + requestConfig.path()));
        runtimeDefinition.setHttpMethod(Optional.ofNullable(requestConfig.method()).orElse("GET").trim().toUpperCase(Locale.ROOT));
        runtimeDefinition.setPath(Optional.ofNullable(requestConfig.path()).orElse(""));
        runtimeDefinition.setRequestJson(ApiAutomationJsonSupport.toJson(requestConfig, "Failed to serialize custom request"));
        runtimeDefinition.setAssertionsJson(ApiAutomationJsonSupport.toJson(defaultList(step.assertions()), "Failed to serialize custom request assertions"));
        runtimeDefinition.setPreprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(step.preProcessors(), "PRE"),
                "Failed to serialize custom request preprocessors"));
        runtimeDefinition.setPostprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(step.postProcessors(), "POST"),
                "Failed to serialize custom request postprocessors"));
        runtimeDefinition.setExtractorsJson("[]");
        return executeDefinition(runtimeDefinition, runtimeDefinition.getDefinitionName(), stepOrder, variables, environment);
    }

    private List<ApiAssertionResult> evaluateAssertions(
            List<ApiAssertionInput> assertions,
            ApiRequestSnapshot request,
            ApiResponseSnapshot response,
            long durationMs,
            Map<String, String> variables
    ) {
        return assertionEvaluator.evaluate(assertions, request, response, durationMs, variables);
    }

    void persistStep(ReportEntity report, Long workspaceId, RunStepComputation computation) {
        runResultPersistenceSupport.persistStep(report, workspaceId, computation);
    }

    void finalizeRunDefinition(ApiDefinitionEntity definition, boolean success, TaskEntity task, ReportEntity report, RunStepComputation step) {
        String result = success ? "SUCCESS" : "FAILED";
        definition.setLastRunResult(result);
        definition.setLastRunAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        finalizeRunTaskAndReport(task, report, result, step.response().errorMessage());
    }

    void finalizeRunCase(ApiDefinitionCaseEntity apiCase, boolean success, TaskEntity task, ReportEntity report, RunStepComputation step) {
        String result = success ? "SUCCESS" : "FAILED";
        apiCase.setLastRunResult(result);
        apiCase.setLastRunAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(apiCase);
        finalizeRunTaskAndReport(task, report, result, step.response().errorMessage());
    }

    void finalizeRunScenario(ApiScenarioEntity scenario, boolean success, String failureSummary, TaskEntity task, ReportEntity report) {
        String result = success ? "SUCCESS" : "FAILED";
        scenario.setLastRunResult(result);
        scenario.setLastRunAt(LocalDateTime.now());
        scenario.setUpdatedAt(LocalDateTime.now());
        scenarioMapper.updateById(scenario);
        finalizeRunTaskAndReport(task, report, result, failureSummary);
    }

    void finalizeRunTaskAndReport(TaskEntity task, ReportEntity report, String result, String failureSummary) {
        task.setTaskStatus(result);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        report.setResult(result);
        report.setFailureSummary(blankToNull(failureSummary));
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    void persistCaseRunHistory(
            ApiDefinitionCaseEntity apiCase,
            ReportEntity report,
            RunStepComputation step,
            Long environmentId,
            Long variableSetId
    ) {
        runResultPersistenceSupport.persistCaseRunHistory(apiCase, report, step, environmentId, variableSetId);
    }

    private String extractJsonValue(String body, String expression) throws IOException {
        if (body == null || body.isBlank()) {
            return "";
        }
        JsonNode current = OBJECT_MAPPER.readTree(body);
        String normalized = Optional.ofNullable(expression).orElse("").trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return current.isValueNode() ? current.asText() : current.toString();
        }
        for (String segment : normalized.split("\\.")) {
            Matcher matcher = Pattern.compile("([\\w-]+)(\\[(\\d+)])?").matcher(segment);
            if (!matcher.matches()) {
                return "";
            }
            current = current.path(matcher.group(1));
            if (matcher.group(3) != null) {
                current = current.path(Integer.parseInt(matcher.group(3)));
            }
        }
        return current.isMissingNode() || current.isNull() ? "" : (current.isValueNode() ? current.asText() : current.toString());
    }

    private String extractValue(ApiResponseSnapshot response, String sourceType, String expression) throws IOException {
        String type = Optional.ofNullable(sourceType).orElse("").trim().toUpperCase();
        return switch (type) {
            case "BODY_JSONPATH" -> extractJsonValue(response.body(), expression);
            case "HEADER" -> Optional.ofNullable(response.headers().get(expression)).orElse("");
            case "STATUS_CODE" -> String.valueOf(response.statusCode());
            default -> throw new BadRequestException("Unsupported extractor type: " + type);
        };
    }

    String firstFailedMessage(List<ApiAssertionResult> results) {
        return assertionSupport.firstFailedMessage(results);
    }

    private List<ApiProcessorInput> readPreProcessors(ApiDefinitionEntity entity) {
        return normalizeProcessors(ApiAutomationFormatSupport.readProcessorsJson(entity.getPreprocessorsJson()), "PRE");
    }

    private List<ApiProcessorInput> readPostProcessors(ApiDefinitionEntity entity) {
        return normalizePostProcessors(
                ApiAutomationFormatSupport.readProcessorsJson(entity.getPostprocessorsJson()),
                ApiAutomationFormatSupport.readExtractors(entity.getExtractorsJson())
        );
    }

    private List<ApiScenarioAssertionInput> normalizeScenarioAssertions(List<ApiScenarioAssertionInput> assertions) {
        List<ApiScenarioAssertionInput> normalized = new ArrayList<>();
        int index = 0;
        for (ApiScenarioAssertionInput assertion : defaultList(assertions)) {
            if (assertion == null || Boolean.FALSE.equals(assertion.enabled())) {
                continue;
            }
            String assertionType = blankToFallback(assertion.assertionType(), "ALL_STEPS_PASSED").toUpperCase(Locale.ROOT);
            String operator = blankToFallback(assertion.operator(), defaultScenarioAssertionOperator(assertionType)).toUpperCase(Locale.ROOT);
            normalized.add(new ApiScenarioAssertionInput(
                    blankToFallback(assertion.id(), "scenario-assertion-" + index++),
                    blankToFallback(assertion.name(), defaultScenarioAssertionName(assertionType)),
                    assertionType,
                    operator,
                    Optional.ofNullable(assertion.expectedValue()).orElse(""),
                    true
            ));
        }
        return normalized;
    }

    List<ApiAssertionResult> evaluateScenarioAssertions(
            List<ApiScenarioAssertionInput> assertions,
            List<ApiRunStepResultResponse> responses
    ) {
        List<ApiAssertionResult> results = new ArrayList<>();
        int failedCount = (int) defaultList(responses).stream().filter(step -> !step.success()).count();
        int stepCount = defaultList(responses).size();
        long totalDuration = defaultList(responses).stream()
                .map(ApiRunStepResultResponse::durationMs)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
        for (ApiScenarioAssertionInput assertion : normalizeScenarioAssertions(assertions)) {
            String type = blankToFallback(assertion.assertionType(), "ALL_STEPS_PASSED").toUpperCase(Locale.ROOT);
            String actual = switch (type) {
                case "FAILED_COUNT_EQUALS", "FAILED_COUNT_LTE" -> String.valueOf(failedCount);
                case "TOTAL_DURATION_LT" -> String.valueOf(totalDuration);
                case "STEP_COUNT_EQUALS" -> String.valueOf(stepCount);
                default -> failedCount == 0 ? "true" : "false";
            };
            String expected = switch (type) {
                case "ALL_STEPS_PASSED" -> "true";
                default -> Optional.ofNullable(assertion.expectedValue()).orElse("0");
            };
            String condition = scenarioAssertionCondition(type, assertion.operator());
            ApiAssertionSupport.ApiAssertionComparison comparison = assertionSupport.compareValue(actual, condition, expected);
            results.add(new ApiAssertionResult(
                    assertion.id(),
                    "SCENARIO",
                    assertion.name(),
                    type,
                    condition,
                    expected,
                    actual,
                    comparison.success(),
                    comparison.message()
            ));
        }
        return results;
    }

    private String scenarioAssertionCondition(String assertionType, String operator) {
        String type = Optional.ofNullable(assertionType).orElse("").trim().toUpperCase(Locale.ROOT);
        if ("ALL_STEPS_PASSED".equals(type)) {
            return "EQUALS";
        }
        if ("FAILED_COUNT_LTE".equals(type)) {
            return "LT_OR_EQUALS";
        }
        if ("TOTAL_DURATION_LT".equals(type)) {
            return "LT";
        }
        return assertionSupport.normalizeAssertionCondition(operator, defaultScenarioAssertionOperator(type));
    }

    private String defaultScenarioAssertionOperator(String assertionType) {
        return switch (Optional.ofNullable(assertionType).orElse("").trim().toUpperCase(Locale.ROOT)) {
            case "FAILED_COUNT_LTE" -> "LT_OR_EQUALS";
            case "TOTAL_DURATION_LT" -> "LT";
            default -> "EQUALS";
        };
    }

    private String defaultScenarioAssertionName(String assertionType) {
        return switch (Optional.ofNullable(assertionType).orElse("").trim().toUpperCase(Locale.ROOT)) {
            case "FAILED_COUNT_EQUALS" -> "Failed count equals";
            case "FAILED_COUNT_LTE" -> "Failed count less than or equals";
            case "TOTAL_DURATION_LT" -> "Total duration less than";
            case "STEP_COUNT_EQUALS" -> "Step count equals";
            default -> "All steps passed";
        };
    }

    private List<ApiAssertionResult> readAssertionResults(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiExtractionResult> readExtractionResults(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiProcessorResult> readProcessorResults(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private String normalizeLoopType(String loopType) {
        String normalized = Optional.ofNullable(loopType).orElse("FIXED").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WHILE", "FOREACH" -> normalized;
            default -> "FIXED";
        };
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BadRequestException("Status must be 0 or 1");
        }
        return status;
    }

    private <T> void applyWorkspaceScope(LambdaQueryWrapper<T> query, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> column, String workspaceCode) {
        workspaceScopeSupport.applyWorkspaceScope(query, column, workspaceCode);
    }

    private WorkspaceEntity resolveScopedWorkspace(String workspaceCode) {
        return workspaceScopeSupport.resolveScopedWorkspace(workspaceCode);
    }

    private void validateReadable(Long workspaceId, String workspaceCode, String message) {
        workspaceScopeSupport.validateReadable(workspaceId, workspaceCode, message);
    }

    ApiDefinitionEntity requireDefinition(Long id) {
        ApiDefinitionEntity entity = definitionMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API definition not found");
        }
        return entity;
    }

    ApiDefinitionCaseEntity requireCase(Long id) {
        ApiDefinitionCaseEntity entity = caseMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API case not found");
        }
        return entity;
    }

    ApiScenarioEntity requireScenario(Long id) {
        ApiScenarioEntity entity = scenarioMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API scenario not found");
        }
        return entity;
    }

    private ApiScenarioModuleEntity requireScenarioModule(Long id) {
        ApiScenarioModuleEntity entity = scenarioModuleMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API scenario module not found");
        }
        return entity;
    }

    private ApiDefinitionModuleEntity requireDefinitionModule(Long id) {
        ApiDefinitionModuleEntity entity = definitionModuleMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API definition module not found");
        }
        return entity;
    }

    private EnvConfigEntity requireEnvironment(Long id) {
        EnvConfigEntity entity = envConfigMapper.selectById(id);
        if (entity == null || !API_ENV_TYPE.equals(entity.getEnvType())) {
            throw new NotFoundException("API environment not found");
        }
        return entity;
    }

    private ParamSetEntity requireVariableSet(Long id) {
        ParamSetEntity entity = paramSetMapper.selectById(id);
        if (entity == null || !API_VARIABLE_SET_TYPE.equals(entity.getParamType())) {
            throw new NotFoundException("API variable set not found");
        }
        return entity;
    }

    private ReportEntity requireReport(Long id) {
        ReportEntity entity = reportMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Report not found");
        }
        return entity;
    }

    private void ensureDefinitionInWorkspace(ApiDefinitionEntity definition, Long workspaceId, String message) {
        if (!definition.getWorkspaceId().equals(workspaceId)) {
            throw new BadRequestException(message);
        }
    }

    private ApiRequestConfigInput readStoredRequestConfig(String json, String methodFallback, String pathFallback) {
        return ApiAutomationJsonSupport.read(json, ApiRequestConfigInput.class,
                new ApiRequestConfigInput(methodFallback, pathFallback, 10000, List.of(), List.of(), List.of(),
                        new ApiRequestBodyInput("NONE", null, List.of(), null, null, null), emptyAuthConfig()));
    }

    private List<ApiScenarioStepInput> normalizeScenarioSteps(List<ApiScenarioStepInput> steps, Long workspaceId) {
        List<ApiScenarioStepInput> normalized = new ArrayList<>();
        for (ApiScenarioStepInput step : steps) {
            if (step == null) {
                continue;
            }
            String stepType = normalizeScenarioStepType(step);
            Long resourceId = step.resourceId();
            ApiRequestConfigInput requestConfig = step.requestConfig();
            List<ApiScenarioStepInput> children = normalizeScenarioSteps(defaultList(step.children()), workspaceId);

            if (SCENARIO_STEP_API.equals(stepType)) {
                resourceId = normalizeScenarioResourceId(step);
                ApiDefinitionEntity definition = requireDefinition(resourceId);
                if (!definition.getWorkspaceId().equals(workspaceId)) {
                    throw new BadRequestException("Scenario steps must belong to the same workspace");
                }
            } else if (SCENARIO_STEP_API_CASE.equals(stepType)) {
                resourceId = normalizeScenarioResourceId(step);
                ApiDefinitionCaseEntity apiCase = requireCase(resourceId);
                if (!apiCase.getWorkspaceId().equals(workspaceId)) {
                    throw new BadRequestException("Scenario steps must belong to the same workspace");
                }
            } else if (SCENARIO_STEP_API_SCENARIO.equals(stepType)) {
                resourceId = normalizeScenarioResourceId(step);
                ApiScenarioEntity scenario = requireScenario(resourceId);
                if (!scenario.getWorkspaceId().equals(workspaceId)) {
                    throw new BadRequestException("Referenced scenario must belong to the same workspace");
                }
            } else if (SCENARIO_STEP_CUSTOM_REQUEST.equals(stepType)) {
                requestConfig = normalizeScenarioRequestConfig(requestConfig);
            } else if (SCENARIO_STEP_CONSTANT_TIMER.equals(stepType)) {
                children = List.of();
            } else if (SCENARIO_STEP_SCRIPT.equals(stepType)) {
                if (blankToNull(step.script()) == null) {
                    throw new BadRequestException("Script step content cannot be blank");
                }
                children = List.of();
            }

            normalized.add(new ApiScenarioStepInput(
                    blankToFallback(step.id(), "scenario-step-" + normalized.size()),
                    blankToNull(step.stepName()),
                    stepType,
                    normalizeScenarioResourceTypeForStep(stepType),
                    resourceId,
                    !Boolean.FALSE.equals(step.enabled()),
                    requestConfig,
                    defaultList(step.assertions()),
                    normalizeProcessors(step.preProcessors(), "PRE"),
                    normalizeProcessors(step.postProcessors(), "POST"),
                    normalizeScenarioDelayMs(step.delayMs()),
                    blankToFallback(step.conditionType(), "EXPRESSION").toUpperCase(Locale.ROOT),
                    blankToNull(step.conditionExpression()),
                    normalizeLoopType(step.loopType()),
                    normalizeScenarioLoopCount(step.loopCount()),
                    blankToNull(step.foreachExpression()),
                    Optional.ofNullable(step.script()).orElse(""),
                    children
            ));
        }
        return normalized;
    }

    private long countScenarioReferences(Long workspaceId, String resourceType, Long resourceId) {
        return scenarioMapper.selectList(new LambdaQueryWrapper<ApiScenarioEntity>()
                        .eq(ApiScenarioEntity::getWorkspaceId, workspaceId))
                .stream()
                .filter(entity -> containsScenarioReference(readScenarioSteps(entity.getStepsJson()), resourceType, resourceId))
                .count();
    }

    private List<Long> definitionModuleDescendantIds(Long workspaceId, Long moduleId) {
        List<ApiDefinitionModuleEntity> modules = definitionModuleMapper.selectList(new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId));
        List<Long> ids = new ArrayList<>();
        collectDefinitionModuleDescendantIds(modules, moduleId, ids);
        return ids.isEmpty() ? List.of(moduleId) : ids;
    }

    private void collectDefinitionModuleDescendantIds(List<ApiDefinitionModuleEntity> modules, Long parentId, List<Long> ids) {
        ids.add(parentId);
        for (ApiDefinitionModuleEntity module : modules) {
            if (parentId.equals(module.getParentId())) {
                collectDefinitionModuleDescendantIds(modules, module.getId(), ids);
            }
        }
    }

    private List<ApiDefinitionModuleItem> buildDefinitionModuleTree(
            List<ApiDefinitionModuleEntity> modules,
            Map<Long, String> pathMap,
            Map<Long, Long> counts,
            Long parentId
    ) {
        return modules.stream()
                .filter(module -> parentId == null ? module.getParentId() == null : parentId.equals(module.getParentId()))
                .sorted(Comparator.comparing(ApiDefinitionModuleEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ApiDefinitionModuleEntity::getId))
                .map(module -> {
                    List<ApiDefinitionModuleItem> children = buildDefinitionModuleTree(modules, pathMap, counts, module.getId());
                    long childCount = children.stream().map(ApiDefinitionModuleItem::definitionCount).mapToLong(Long::longValue).sum();
                    long ownCount = Optional.ofNullable(counts.get(module.getId())).orElse(0L);
                    return toDefinitionModuleItem(module, pathMap, ownCount + childCount, children);
                })
                .toList();
    }

    private ApiDefinitionModuleItem toDefinitionModuleItem(
            ApiDefinitionModuleEntity entity,
            Map<Long, String> pathMap,
            Long count,
            List<ApiDefinitionModuleItem> children
    ) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiDefinitionModuleItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getParentId(),
                entity.getModuleName(),
                pathMap.getOrDefault(entity.getId(), entity.getModuleName()),
                entity.getSortOrder(),
                Optional.ofNullable(count).orElse(0L),
                defaultList(children)
        );
    }

    private Map<Long, String> currentDefinitionModulePathMap(Long workspaceId) {
        return buildDefinitionModulePathMap(definitionModuleMapper.selectList(new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId)));
    }

    private Map<Long, String> buildDefinitionModulePathMap(List<ApiDefinitionModuleEntity> modules) {
        Map<Long, ApiDefinitionModuleEntity> moduleMap = modules.stream()
                .collect(java.util.stream.Collectors.toMap(ApiDefinitionModuleEntity::getId, module -> module));
        Map<Long, String> pathMap = new HashMap<>();
        for (ApiDefinitionModuleEntity module : modules) {
            pathMap.put(module.getId(), buildDefinitionModulePath(module, moduleMap));
        }
        return pathMap;
    }

    private String buildDefinitionModulePath(ApiDefinitionModuleEntity module, Map<Long, ApiDefinitionModuleEntity> moduleMap) {
        List<String> segments = new ArrayList<>();
        ApiDefinitionModuleEntity current = module;
        while (current != null) {
            segments.add(0, current.getModuleName());
            current = current.getParentId() == null ? null : moduleMap.get(current.getParentId());
        }
        return String.join("/", segments);
    }

    private String getDefinitionModulePath(ApiDefinitionModuleEntity module) {
        return currentDefinitionModulePathMap(module.getWorkspaceId()).getOrDefault(module.getId(), module.getModuleName());
    }

    private Long findDefinitionModuleIdByPath(
            List<ApiDefinitionModuleEntity> modules,
            Map<Long, String> pathMap,
            Long workspaceId,
            String path
    ) {
        String normalizedPath = blankToNull(path);
        if (normalizedPath == null) {
            return null;
        }
        return modules.stream()
                .filter(module -> module.getWorkspaceId().equals(workspaceId))
                .filter(module -> normalizedPath.equals(pathMap.get(module.getId())))
                .map(ApiDefinitionModuleEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private void ensureDefinitionModuleNameUnique(Long workspaceId, Long parentId, Long excludeId, String name) {
        String moduleName = blankToNull(name);
        if (moduleName == null) {
            throw new BadRequestException("Module name cannot be blank");
        }
        if (moduleName.contains("/")) {
            throw new BadRequestException("Module name cannot contain /");
        }
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId)
                .eq(ApiDefinitionModuleEntity::getModuleName, moduleName);
        if (parentId == null) {
            query.isNull(ApiDefinitionModuleEntity::getParentId);
        } else {
            query.eq(ApiDefinitionModuleEntity::getParentId, parentId);
        }
        if (excludeId != null) {
            query.ne(ApiDefinitionModuleEntity::getId, excludeId);
        }
        if (definitionModuleMapper.selectCount(query) > 0) {
            throw new BadRequestException("Module name already exists");
        }
    }

    private int nextDefinitionModuleSort(Long workspaceId, Long parentId) {
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId);
        if (parentId == null) {
            query.isNull(ApiDefinitionModuleEntity::getParentId);
        } else {
            query.eq(ApiDefinitionModuleEntity::getParentId, parentId);
        }
        return definitionModuleMapper.selectList(query).stream()
                .map(ApiDefinitionModuleEntity::getSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void ensureDefinitionModulePath(Long workspaceId, String directoryName) {
        String normalizedPath = blankToNull(directoryName);
        if (normalizedPath == null) {
            return;
        }
        Long parentId = null;
        for (String segment : normalizedPath.split("/")) {
            String moduleName = blankToNull(segment);
            if (moduleName == null) {
                continue;
            }
            if (moduleName.contains("/")) {
                throw new BadRequestException("Module name cannot contain /");
            }
            ApiDefinitionModuleEntity existing = findDefinitionModuleByName(workspaceId, parentId, moduleName);
            if (existing != null) {
                parentId = existing.getId();
                continue;
            }
            ApiDefinitionModuleEntity entity = new ApiDefinitionModuleEntity();
            entity.setWorkspaceId(workspaceId);
            entity.setParentId(parentId);
            entity.setModuleName(moduleName);
            entity.setSortOrder(nextDefinitionModuleSort(workspaceId, parentId));
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            definitionModuleMapper.insert(entity);
            parentId = entity.getId();
        }
    }

    private void ensureDefinitionModulesFromDefinitions(String workspaceCode) {
        LambdaQueryWrapper<ApiDefinitionEntity> query = new LambdaQueryWrapper<>();
        applyWorkspaceScope(query, ApiDefinitionEntity::getWorkspaceId, workspaceCode);
        definitionMapper.selectList(query).stream()
                .filter(definition -> blankToNull(definition.getDirectoryName()) != null)
                .forEach(definition -> ensureDefinitionModulePath(definition.getWorkspaceId(), definition.getDirectoryName()));
    }

    private ApiDefinitionModuleEntity findDefinitionModuleByName(Long workspaceId, Long parentId, String name) {
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId)
                .eq(ApiDefinitionModuleEntity::getModuleName, name);
        if (parentId == null) {
            query.isNull(ApiDefinitionModuleEntity::getParentId);
        } else {
            query.eq(ApiDefinitionModuleEntity::getParentId, parentId);
        }
        return definitionModuleMapper.selectOne(query);
    }

    private long countDefinitionsInModulePath(Long workspaceId, String modulePath) {
        String normalizedPath = blankToNull(modulePath);
        if (normalizedPath == null) {
            return 0L;
        }
        return definitionMapper.selectList(new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getWorkspaceId, workspaceId))
                .stream()
                .map(ApiDefinitionEntity::getDirectoryName)
                .filter(path -> path != null && (path.equals(normalizedPath) || path.startsWith(normalizedPath + "/")))
                .count();
    }

    private void syncDefinitionDirectoryPrefix(Long workspaceId, String sourcePath, String targetPath) {
        String normalizedSourcePath = blankToNull(sourcePath);
        if (normalizedSourcePath == null) {
            return;
        }
        String normalizedTargetPath = blankToNull(targetPath);
        List<ApiDefinitionEntity> definitions = definitionMapper.selectList(new LambdaQueryWrapper<ApiDefinitionEntity>()
                .eq(ApiDefinitionEntity::getWorkspaceId, workspaceId));
        for (ApiDefinitionEntity definition : definitions) {
            String directoryName = blankToNull(definition.getDirectoryName());
            if (directoryName == null || !(directoryName.equals(normalizedSourcePath) || directoryName.startsWith(normalizedSourcePath + "/"))) {
                continue;
            }
            String suffix = directoryName.equals(normalizedSourcePath)
                    ? ""
                    : directoryName.substring(normalizedSourcePath.length() + 1);
            String nextPath = normalizedTargetPath == null
                    ? (suffix.isBlank() ? null : suffix)
                    : (suffix.isBlank() ? normalizedTargetPath : normalizedTargetPath + "/" + suffix);
            definition.setDirectoryName(nextPath);
            definition.setUpdatedAt(LocalDateTime.now());
            definitionMapper.updateById(definition);
        }
    }

    private List<Long> scenarioModuleDescendantIds(Long workspaceId, Long moduleId) {
        List<ApiScenarioModuleEntity> modules = scenarioModuleMapper.selectList(new LambdaQueryWrapper<ApiScenarioModuleEntity>()
                .eq(ApiScenarioModuleEntity::getWorkspaceId, workspaceId));
        List<Long> ids = new ArrayList<>();
        collectScenarioModuleDescendantIds(modules, moduleId, ids);
        return ids.isEmpty() ? List.of(moduleId) : ids;
    }

    private void collectScenarioModuleDescendantIds(List<ApiScenarioModuleEntity> modules, Long parentId, List<Long> ids) {
        ids.add(parentId);
        for (ApiScenarioModuleEntity module : modules) {
            if (parentId.equals(module.getParentId())) {
                collectScenarioModuleDescendantIds(modules, module.getId(), ids);
            }
        }
    }

    private List<ApiScenarioModuleItem> buildScenarioModuleTree(
            List<ApiScenarioModuleEntity> modules,
            Map<Long, Long> counts,
            Long parentId
    ) {
        return modules.stream()
                .filter(module -> parentId == null ? module.getParentId() == null : parentId.equals(module.getParentId()))
                .sorted(Comparator.comparing(ApiScenarioModuleEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ApiScenarioModuleEntity::getId))
                .map(module -> {
                    List<ApiScenarioModuleItem> children = buildScenarioModuleTree(modules, counts, module.getId());
                    long childCount = children.stream().map(ApiScenarioModuleItem::scenarioCount).mapToLong(Long::longValue).sum();
                    long ownCount = Optional.ofNullable(counts.get(module.getId())).orElse(0L);
                    return toScenarioModuleItem(module, ownCount + childCount, children);
                })
                .toList();
    }

    private ApiScenarioModuleItem toScenarioModuleItem(ApiScenarioModuleEntity entity, Long count, List<ApiScenarioModuleItem> children) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiScenarioModuleItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getParentId(),
                entity.getModuleName(),
                entity.getSortOrder(),
                Optional.ofNullable(count).orElse(0L),
                defaultList(children)
        );
    }

    private void ensureScenarioModuleNameUnique(Long workspaceId, Long parentId, Long excludeId, String name) {
        String moduleName = blankToNull(name);
        if (moduleName == null) {
            throw new BadRequestException("Module name cannot be blank");
        }
        LambdaQueryWrapper<ApiScenarioModuleEntity> query = new LambdaQueryWrapper<ApiScenarioModuleEntity>()
                .eq(ApiScenarioModuleEntity::getWorkspaceId, workspaceId)
                .eq(ApiScenarioModuleEntity::getModuleName, moduleName);
        if (parentId == null) {
            query.isNull(ApiScenarioModuleEntity::getParentId);
        } else {
            query.eq(ApiScenarioModuleEntity::getParentId, parentId);
        }
        if (excludeId != null) {
            query.ne(ApiScenarioModuleEntity::getId, excludeId);
        }
        if (scenarioModuleMapper.selectCount(query) > 0) {
            throw new BadRequestException("Module name already exists");
        }
    }

    private int nextScenarioModuleSort(Long workspaceId, Long parentId) {
        LambdaQueryWrapper<ApiScenarioModuleEntity> query = new LambdaQueryWrapper<ApiScenarioModuleEntity>()
                .eq(ApiScenarioModuleEntity::getWorkspaceId, workspaceId);
        if (parentId == null) {
            query.isNull(ApiScenarioModuleEntity::getParentId);
        } else {
            query.eq(ApiScenarioModuleEntity::getParentId, parentId);
        }
        return scenarioModuleMapper.selectList(query).stream()
                .map(ApiScenarioModuleEntity::getSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private long countScenariosInModule(Long moduleId) {
        return scenarioMapper.selectCount(new LambdaQueryWrapper<ApiScenarioEntity>()
                .eq(ApiScenarioEntity::getModuleId, moduleId));
    }

    private Long ensureDefaultScenarioModule(Long workspaceId) {
        List<ApiScenarioModuleEntity> modules = scenarioModuleMapper.selectList(new LambdaQueryWrapper<ApiScenarioModuleEntity>()
                .eq(ApiScenarioModuleEntity::getWorkspaceId, workspaceId)
                .isNull(ApiScenarioModuleEntity::getParentId)
                .eq(ApiScenarioModuleEntity::getModuleName, "\u9ed8\u8ba4\u6a21\u5757"));
        if (!modules.isEmpty()) {
            return modules.get(0).getId();
        }
        ApiScenarioModuleEntity entity = new ApiScenarioModuleEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setParentId(null);
        entity.setModuleName("\u9ed8\u8ba4\u6a21\u5757");
        entity.setSortOrder(nextScenarioModuleSort(workspaceId, null));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        scenarioModuleMapper.insert(entity);
        return entity.getId();
    }

    private String normalizeScenarioPriority(String priority) {
        String normalized = blankToFallback(priority, "P1").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "P1", "P2", "P3" -> normalized;
            default -> "P1";
        };
    }

    private String normalizeScenarioStatus(String status) {
        String normalized = blankToFallback(status, "IN_PROGRESS").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NOT_STARTED", "IN_PROGRESS", "COMPLETED", "ARCHIVED" -> normalized;
            default -> "IN_PROGRESS";
        };
    }

    private int countScenarioSteps(List<ApiScenarioStepInput> steps) {
        int count = 0;
        for (ApiScenarioStepInput step : defaultList(steps)) {
            if (step == null) {
                continue;
            }
            count++;
            count += countScenarioSteps(step.children());
        }
        return count;
    }

    private boolean containsScenarioReference(List<ApiScenarioStepInput> steps, String resourceType, Long resourceId) {
        for (ApiScenarioStepInput step : defaultList(steps)) {
            if (step == null) {
                continue;
            }
            String normalizedResourceType = normalizeScenarioResourceTypeForStep(normalizeScenarioStepType(step));
            if (resourceType.equals(normalizedResourceType) && resourceId.equals(step.resourceId())) {
                return true;
            }
            if (containsScenarioReference(step.children(), resourceType, resourceId)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeScenarioStepType(ApiScenarioStepInput step) {
        String rawType = blankToNull(step.stepType());
        if (rawType == null) {
            String resourceType = Optional.ofNullable(step.resourceType()).orElse(SCENARIO_RESOURCE_TYPE_DEFINITION).trim().toUpperCase(Locale.ROOT);
            rawType = SCENARIO_RESOURCE_TYPE_CASE.equals(resourceType) ? SCENARIO_STEP_API_CASE : SCENARIO_STEP_API;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case SCENARIO_RESOURCE_TYPE_DEFINITION -> SCENARIO_STEP_API;
            case SCENARIO_RESOURCE_TYPE_CASE -> SCENARIO_STEP_API_CASE;
            case SCENARIO_STEP_API, SCENARIO_STEP_API_CASE, SCENARIO_STEP_CUSTOM_REQUEST, SCENARIO_STEP_API_SCENARIO,
                 SCENARIO_STEP_IF_CONTROLLER, SCENARIO_STEP_LOOP_CONTROLLER, SCENARIO_STEP_ONCE_ONLY_CONTROLLER,
                 SCENARIO_STEP_CONSTANT_TIMER, SCENARIO_STEP_SCRIPT -> normalized;
            default -> throw new BadRequestException("Unsupported scenario step type: " + normalized);
        };
    }

    private String normalizeScenarioResourceTypeForStep(String stepType) {
        return switch (stepType) {
            case SCENARIO_STEP_API -> SCENARIO_RESOURCE_TYPE_DEFINITION;
            case SCENARIO_STEP_API_CASE -> SCENARIO_RESOURCE_TYPE_CASE;
            default -> null;
        };
    }

    private Long normalizeScenarioResourceId(ApiScenarioStepInput step) {
        Long resourceId = step.resourceId();
        if (resourceId == null) {
            throw new BadRequestException("Scenario step resource cannot be blank");
        }
        return resourceId;
    }

    private ApiRequestConfigInput normalizeScenarioRequestConfig(ApiRequestConfigInput requestConfig) {
        if (requestConfig == null) {
            throw new BadRequestException("Custom request step requires request config");
        }
        String method = Optional.ofNullable(requestConfig.method()).orElse("").trim().toUpperCase(Locale.ROOT);
        String path = Optional.ofNullable(requestConfig.path()).orElse("").trim();
        if (method.isBlank() || path.isBlank()) {
            throw new BadRequestException("Custom request method and path cannot be blank");
        }
        return new ApiRequestConfigInput(
                method,
                path,
                requestConfig.timeoutMs() == null || requestConfig.timeoutMs() <= 0 ? 10000 : requestConfig.timeoutMs(),
                defaultList(requestConfig.queryParams()),
                defaultList(requestConfig.headers()),
                defaultList(requestConfig.cookies()),
                requestConfig.body() == null ? new ApiRequestBodyInput("NONE", null, List.of(), null, null, null) : requestConfig.body(),
                normalizeAuth(requestConfig.authConfig())
        );
    }

    private Integer normalizeScenarioDelayMs(Integer delayMs) {
        if (delayMs == null) {
            return 1000;
        }
        return Math.max(1, Math.min(MAX_SCENARIO_WAIT_MS, delayMs));
    }

    private Integer normalizeScenarioLoopCount(Integer loopCount) {
        if (loopCount == null) {
            return 1;
        }
        return Math.max(0, Math.min(MAX_SCENARIO_LOOP_COUNT, loopCount));
    }

    private MutableRequestConfig toMutableRequestConfig(ApiRequestConfigInput config) {
        return new MutableRequestConfig(
                Optional.ofNullable(config.method()).orElse("GET"),
                Optional.ofNullable(config.path()).orElse(""),
                config.timeoutMs(),
                new ArrayList<>(defaultList(config.queryParams())),
                new ArrayList<>(defaultList(config.headers())),
                new ArrayList<>(defaultList(config.cookies())),
                config.body() == null ? new ApiRequestBodyInput("NONE", null, List.of(), null, null, null) : config.body(),
                normalizeAuth(config.authConfig())
        );
    }

    private ApiRequestConfigInput toRequestConfig(MutableRequestConfig config) {
        return new ApiRequestConfigInput(
                config.method(),
                config.path(),
                config.timeoutMs(),
                config.queryParams(),
                config.headers(),
                config.cookies(),
                config.body(),
                config.authConfig()
        );
    }

    record EnvironmentConfigPayload(
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig,
            Integer timeoutMs,
            List<ApiVariableItem> variables
    ) {
    }

    record ResolvedEnvironment(
            Long environmentId,
            String baseUrl,
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig,
            Integer timeoutMs,
            List<ApiVariableItem> variables
    ) {
    }

    record ExecutionContext(
            ResolvedEnvironment environment,
            Map<String, String> variables
    ) {
    }

    record RunEnvelope(
            TaskEntity task,
            ReportEntity report
    ) {
    }

    record RunStepComputation(
            boolean success,
            ApiRunStepResultResponse response
    ) {
    }

    static class MutableRequestConfig {
        private String method;
        private String path;
        private Integer timeoutMs;
        private List<ApiKeyValueInput> queryParams;
        private List<ApiKeyValueInput> headers;
        private List<ApiKeyValueInput> cookies;
        private ApiRequestBodyInput body;
        private ApiAuthConfigInput authConfig;

        MutableRequestConfig(
                String method,
                String path,
                Integer timeoutMs,
                List<ApiKeyValueInput> queryParams,
                List<ApiKeyValueInput> headers,
                List<ApiKeyValueInput> cookies,
                ApiRequestBodyInput body,
                ApiAuthConfigInput authConfig
        ) {
            this.method = method;
            this.path = path;
            this.timeoutMs = timeoutMs;
            this.queryParams = queryParams;
            this.headers = headers;
            this.cookies = cookies;
            this.body = body;
            this.authConfig = authConfig;
        }

        String method() {
            return method;
        }

        String path() {
            return path;
        }

        Integer timeoutMs() {
            return timeoutMs;
        }

        List<ApiKeyValueInput> queryParams() {
            return queryParams;
        }

        List<ApiKeyValueInput> headers() {
            return headers;
        }

        List<ApiKeyValueInput> cookies() {
            return cookies;
        }

        ApiRequestBodyInput body() {
            return body;
        }

        ApiAuthConfigInput authConfig() {
            return authConfig;
        }

        Map<String, Object> toScriptMap() {
            LinkedHashMap<String, Object> request = new LinkedHashMap<>();
            request.put("method", method);
            request.put("path", path);
            request.put("timeoutMs", timeoutMs);
            request.put("queryParams", new ArrayList<>(queryParams));
            request.put("headers", new ArrayList<>(headers));
            request.put("cookies", new ArrayList<>(cookies));
            request.put("body", body);
            request.put("authConfig", authConfig);
            return request;
        }

        void applyScriptMap(Map<String, Object> requestValues) {
            ApiRequestConfigInput next = OBJECT_MAPPER.convertValue(requestValues, ApiRequestConfigInput.class);
            method = blankToFallback(next.method(), method).toUpperCase();
            path = blankToFallback(next.path(), path);
            timeoutMs = next.timeoutMs() == null ? timeoutMs : next.timeoutMs();
            queryParams = new ArrayList<>(defaultList(next.queryParams()));
            headers = new ArrayList<>(defaultList(next.headers()));
            cookies = new ArrayList<>(defaultList(next.cookies()));
            body = next.body() == null ? body : next.body();
            authConfig = next.authConfig() == null ? authConfig : normalizeAuth(next.authConfig());
        }
    }

}

