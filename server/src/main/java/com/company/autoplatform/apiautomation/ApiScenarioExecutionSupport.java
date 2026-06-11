package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.blankToFallback;
import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.defaultList;
import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Component
public class ApiScenarioExecutionSupport {

    private static final String SCENARIO_STEP_API = "API";
    private static final String SCENARIO_STEP_API_CASE = "API_CASE";
    private static final String SCENARIO_STEP_CUSTOM_REQUEST = "CUSTOM_REQUEST";
    private static final String SCENARIO_STEP_API_SCENARIO = "API_SCENARIO";
    private static final String SCENARIO_STEP_IF_CONTROLLER = "IF_CONTROLLER";
    private static final String SCENARIO_STEP_LOOP_CONTROLLER = "LOOP_CONTROLLER";
    private static final String SCENARIO_STEP_ONCE_ONLY_CONTROLLER = "ONCE_ONLY_CONTROLLER";
    private static final String SCENARIO_STEP_CONSTANT_TIMER = "CONSTANT_TIMER";
    private static final String SCENARIO_STEP_SCRIPT = "SCRIPT";
    private static final int MAX_SCENARIO_NESTING_DEPTH = 3;
    private static final int MAX_SCENARIO_LOOP_COUNT = 50;
    private static final int MAX_SCENARIO_WAIT_MS = 60000;

    private final ApiAutomationScriptRunner scriptRunner;
    private final ApiVariableResolver variableResolver;
    private final ApiAssertionEvaluator assertionEvaluator;
    private final ApiAssertionSupport assertionSupport;

    public ApiScenarioExecutionSupport(
            ApiAutomationScriptRunner scriptRunner,
            ApiVariableResolver variableResolver,
            ApiAssertionEvaluator assertionEvaluator,
            ApiAssertionSupport assertionSupport
    ) {
        this.scriptRunner = scriptRunner;
        this.variableResolver = variableResolver;
        this.assertionEvaluator = assertionEvaluator;
        this.assertionSupport = assertionSupport;
    }

    List<ApiExecutionRuntimeModels.RunStepComputation> executeScenarioSteps(
            List<ApiScenarioStepInput> steps,
            int[] stepOrder,
            Map<String, String> variables,
            ApiExecutionRuntimeModels.ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            Long rootScenarioId,
            int nestingDepth,
            Set<String> onceOnlyKeys,
            boolean continueOnFailure,
            ScenarioExecutionDelegate delegate
    ) {
        List<ApiExecutionRuntimeModels.RunStepComputation> results = new ArrayList<>();
        for (ApiScenarioStepInput step : defaultList(steps)) {
            if (step == null || Boolean.FALSE.equals(step.enabled())) {
                continue;
            }
            String stepType = delegate.normalizeScenarioStepType(step);
            int resultStart = results.size();
            try {
                switch (stepType) {
                    case SCENARIO_STEP_API, SCENARIO_STEP_API_CASE, SCENARIO_STEP_CUSTOM_REQUEST ->
                            results.add(executeScenarioStep(step, stepOrder[0]++, variables, environment, workspaceCode, workspaceId, delegate));
                    case SCENARIO_STEP_API_SCENARIO -> results.addAll(executeReferencedScenarioStep(
                            step, stepOrder, variables, environment, workspaceCode, workspaceId, rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
                    case SCENARIO_STEP_IF_CONTROLLER -> results.addAll(executeIfControllerStep(
                            step, stepOrder, variables, environment, workspaceCode, workspaceId, rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
                    case SCENARIO_STEP_LOOP_CONTROLLER -> results.addAll(executeLoopControllerStep(
                            step, stepOrder, variables, environment, workspaceCode, workspaceId, rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
                    case SCENARIO_STEP_ONCE_ONLY_CONTROLLER -> results.addAll(executeOnceOnlyControllerStep(
                            step, stepOrder, variables, environment, workspaceCode, workspaceId, rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
                    case SCENARIO_STEP_CONSTANT_TIMER -> results.add(executeConstantTimerStep(step, stepOrder[0]++));
                    case SCENARIO_STEP_SCRIPT -> results.add(executeScriptScenarioStep(step, stepOrder[0]++, variables));
                    default -> results.add(syntheticScenarioStep(
                            stepOrder[0]++,
                            blankToFallback(step.stepName(), stepType),
                            false,
                            0L,
                            "Unsupported scenario step type: " + stepType,
                            List.of()
                    ));
                }
            } catch (RuntimeException exception) {
                results.add(syntheticScenarioStep(
                        stepOrder[0]++,
                        blankToFallback(step.stepName(), stepType),
                        false,
                        0L,
                        blankToFallback(exception.getMessage(), "Scenario step failed"),
                        List.of()
                ));
            }
            if (!continueOnFailure && results.subList(resultStart, results.size()).stream().anyMatch(result -> !result.success())) {
                break;
            }
        }
        return results;
    }

    private ApiExecutionRuntimeModels.RunStepComputation executeScenarioStep(
            ApiScenarioStepInput step,
            int stepOrder,
            Map<String, String> variables,
            ApiExecutionRuntimeModels.ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            ScenarioExecutionDelegate delegate
    ) {
        String stepType = delegate.normalizeScenarioStepType(step);
        if (SCENARIO_STEP_API_CASE.equals(stepType)) {
            Long resourceId = delegate.normalizeScenarioResourceId(step);
            ApiDefinitionCaseEntity apiCase = delegate.requireCase(resourceId);
            delegate.validateReadable(apiCase.getWorkspaceId(), workspaceCode, "Scenario contains an inaccessible case");
            return delegate.executeCase(
                    apiCase,
                    blankToFallback(step.stepName(), apiCase.getCaseName()),
                    stepOrder,
                    variables,
                    environment
            );
        }
        if (SCENARIO_STEP_CUSTOM_REQUEST.equals(stepType)) {
            return delegate.executeCustomRequestStep(step, stepOrder, variables, environment, workspaceId);
        }
        Long resourceId = delegate.normalizeScenarioResourceId(step);
        ApiDefinitionEntity definition = delegate.requireDefinition(resourceId);
        delegate.validateReadable(definition.getWorkspaceId(), workspaceCode, "Scenario contains an inaccessible definition");
        return delegate.executeDefinition(
                definition,
                blankToFallback(step.stepName(), definition.getDefinitionName()),
                stepOrder,
                variables,
                environment
        );
    }

    private List<ApiExecutionRuntimeModels.RunStepComputation> executeReferencedScenarioStep(
            ApiScenarioStepInput step,
            int[] stepOrder,
            Map<String, String> variables,
            ApiExecutionRuntimeModels.ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            Long rootScenarioId,
            int nestingDepth,
            Set<String> onceOnlyKeys,
            boolean continueOnFailure,
            ScenarioExecutionDelegate delegate
    ) {
        if (nestingDepth >= MAX_SCENARIO_NESTING_DEPTH) {
            return List.of(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), "Referenced Scenario"),
                    false, 0L, "Scenario nesting depth exceeds " + MAX_SCENARIO_NESTING_DEPTH, List.of()));
        }
        Long scenarioId = delegate.normalizeScenarioResourceId(step);
        if (scenarioId.equals(rootScenarioId)) {
            return List.of(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), "Referenced Scenario"),
                    false, 0L, "Scenario circular reference is not allowed", List.of()));
        }
        ApiScenarioEntity scenario = delegate.requireScenario(scenarioId);
        delegate.validateReadable(scenario.getWorkspaceId(), workspaceCode, "Scenario contains an inaccessible referenced scenario");
        if (!scenario.getWorkspaceId().equals(workspaceId)) {
            return List.of(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), scenario.getScenarioName()),
                    false, 0L, "Referenced scenario must belong to the same workspace", List.of()));
        }
        List<ApiScenarioStepInput> childSteps = delegate.readScenarioSteps(scenario.getStepsJson());
        List<ApiExecutionRuntimeModels.RunStepComputation> results = new ArrayList<>();
        results.add(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), scenario.getScenarioName()), true, 0L, null, List.of()));
        results.addAll(executeScenarioSteps(childSteps, stepOrder, variables, environment, workspaceCode, workspaceId,
                rootScenarioId, nestingDepth + 1, onceOnlyKeys, continueOnFailure, delegate));
        return results;
    }

    private List<ApiExecutionRuntimeModels.RunStepComputation> executeIfControllerStep(
            ApiScenarioStepInput step,
            int[] stepOrder,
            Map<String, String> variables,
            ApiExecutionRuntimeModels.ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            Long rootScenarioId,
            int nestingDepth,
            Set<String> onceOnlyKeys,
            boolean continueOnFailure,
            ScenarioExecutionDelegate delegate
    ) {
        boolean matched = evaluateScenarioCondition(step, variables);
        List<ApiExecutionRuntimeModels.RunStepComputation> results = new ArrayList<>();
        results.add(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), "IF Controller"), true, 0L,
                matched ? "Condition matched" : "Condition not matched", List.of()));
        if (matched) {
            results.addAll(executeScenarioSteps(step.children(), stepOrder, variables, environment, workspaceCode, workspaceId,
                    rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
        }
        return results;
    }

    private List<ApiExecutionRuntimeModels.RunStepComputation> executeLoopControllerStep(
            ApiScenarioStepInput step,
            int[] stepOrder,
            Map<String, String> variables,
            ApiExecutionRuntimeModels.ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            Long rootScenarioId,
            int nestingDepth,
            Set<String> onceOnlyKeys,
            boolean continueOnFailure,
            ScenarioExecutionDelegate delegate
    ) {
        List<ApiExecutionRuntimeModels.RunStepComputation> results = new ArrayList<>();
        int loopCount = resolveScenarioLoopCount(step, variables);
        List<String> foreachItems = scenarioForeachItems(step, variables);
        results.add(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), "Loop Controller"), true, 0L,
                "Loop count: " + loopCount, List.of()));
        for (int index = 0; index < loopCount; index++) {
            variables.put("loopIndex", String.valueOf(index));
            if (!foreachItems.isEmpty() && index < foreachItems.size()) {
                variables.put("item", foreachItems.get(index));
            }
            if ("WHILE".equals(normalizeLoopType(step.loopType())) && index > 0 && !evaluateScenarioCondition(step, variables)) {
                break;
            }
            results.addAll(executeScenarioSteps(step.children(), stepOrder, variables, environment, workspaceCode, workspaceId,
                    rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
            if (!continueOnFailure && results.stream().anyMatch(result -> !result.success())) {
                break;
            }
        }
        return results;
    }

    private List<ApiExecutionRuntimeModels.RunStepComputation> executeOnceOnlyControllerStep(
            ApiScenarioStepInput step,
            int[] stepOrder,
            Map<String, String> variables,
            ApiExecutionRuntimeModels.ResolvedEnvironment environment,
            String workspaceCode,
            Long workspaceId,
            Long rootScenarioId,
            int nestingDepth,
            Set<String> onceOnlyKeys,
            boolean continueOnFailure,
            ScenarioExecutionDelegate delegate
    ) {
        String key = blankToFallback(step.id(), blankToFallback(step.stepName(), "once-only-" + stepOrder[0]));
        boolean firstRun = onceOnlyKeys.add(key);
        List<ApiExecutionRuntimeModels.RunStepComputation> results = new ArrayList<>();
        results.add(syntheticScenarioStep(stepOrder[0]++, blankToFallback(step.stepName(), "Once Only Controller"), true, 0L,
                firstRun ? "Executed" : "Skipped", List.of()));
        if (firstRun) {
            results.addAll(executeScenarioSteps(step.children(), stepOrder, variables, environment, workspaceCode, workspaceId,
                    rootScenarioId, nestingDepth, onceOnlyKeys, continueOnFailure, delegate));
        }
        return results;
    }

    private ApiExecutionRuntimeModels.RunStepComputation executeConstantTimerStep(ApiScenarioStepInput step, int stepOrder) {
        int delayMs = Math.max(1, Math.min(MAX_SCENARIO_WAIT_MS, Optional.ofNullable(step.delayMs()).orElse(1000)));
        long started = System.currentTimeMillis();
        sleep(delayMs);
        return syntheticScenarioStep(stepOrder, blankToFallback(step.stepName(), "Constant Timer"), true,
                System.currentTimeMillis() - started, "Waited " + delayMs + " ms", List.of());
    }

    private ApiExecutionRuntimeModels.RunStepComputation executeScriptScenarioStep(
            ApiScenarioStepInput step,
            int stepOrder,
            Map<String, String> variables
    ) {
        long started = System.currentTimeMillis();
        String script = Optional.ofNullable(step.script()).orElse("");
        if (script.isBlank()) {
            return syntheticScenarioStep(stepOrder, blankToFallback(step.stepName(), "Script"), false, 0L,
                    "Script content cannot be blank", List.of());
        }
        ApiAutomationScriptRunner.ScriptExecutionResult scriptResult = scriptRunner.execute(script, new LinkedHashMap<>(variables), Map.of(), Map.of());
        variables.clear();
        variables.putAll(scriptResult.variables());
        ApiProcessorResult processorResult = new ApiProcessorResult(
                "SCENARIO",
                "SCRIPT",
                blankToFallback(step.stepName(), "Script"),
                scriptResult.success(),
                System.currentTimeMillis() - started,
                scriptResult.message(),
                scriptResult.logs(),
                scriptResult.variables()
        );
        long durationMs = System.currentTimeMillis() - started;
        List<ApiAssertionResult> assertionResults = scriptResult.success()
                ? evaluateScriptStepAssertions(step.assertions(), durationMs, variables)
                : List.of();
        boolean assertionsPassed = assertionResults.stream().allMatch(ApiAssertionResult::success);
        boolean success = scriptResult.success() && assertionsPassed;
        String errorMessage = !scriptResult.success() ? scriptResult.message()
                : assertionsPassed ? null : assertionSupport.firstFailedMessage(assertionResults);
        return new ApiExecutionRuntimeModels.RunStepComputation(success, new ApiRunStepResultResponse(
                null,
                null,
                stepOrder,
                blankToFallback(step.stepName(), "Script"),
                null,
                success,
                durationMs,
                null,
                null,
                assertionResults,
                List.of(),
                List.of(processorResult),
                errorMessage,
                LocalDateTime.now()
        ));
    }

    private List<ApiAssertionResult> evaluateScriptStepAssertions(
            List<ApiAssertionInput> assertions,
            long durationMs,
            Map<String, String> variables
    ) {
        List<ApiAssertionInput> scriptAssertions = defaultList(assertions).stream()
                .filter(Objects::nonNull)
                .filter(assertion -> {
                    String type = assertionSupport.normalizeAssertionType(assertion);
                    return "VARIABLE".equals(type) || "SCRIPT".equals(type);
                })
                .toList();
        return assertionEvaluator.evaluate(scriptAssertions, null, null, durationMs, variables);
    }

    private boolean evaluateScenarioCondition(ApiScenarioStepInput step, Map<String, String> variables) {
        String conditionType = blankToFallback(step.conditionType(), "EXPRESSION").toUpperCase(Locale.ROOT);
        String expression = Optional.ofNullable(step.conditionExpression()).orElse("");
        if ("SCRIPT".equals(conditionType)) {
            if (expression.isBlank()) {
                return false;
            }
            ApiAutomationScriptRunner.ScriptExecutionResult scriptResult = scriptRunner.execute(
                    expression,
                    new LinkedHashMap<>(variables),
                    Map.of(),
                    Map.of()
            );
            variables.clear();
            variables.putAll(scriptResult.variables());
            return scriptResult.success();
        }
        String resolved = Optional.ofNullable(variableResolver.replaceVariables(expression, variables)).orElse("").trim();
        if (resolved.isBlank()) {
            return false;
        }
        if ("true".equalsIgnoreCase(resolved)) {
            return true;
        }
        if ("false".equalsIgnoreCase(resolved)) {
            return false;
        }
        Matcher matcher = Pattern.compile("^(.+?)\\s*(==|=|!=|<>|>=|<=|>|<|contains)\\s*(.*)$", Pattern.CASE_INSENSITIVE).matcher(resolved);
        if (!matcher.matches()) {
            return false;
        }
        String actual = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String expected = matcher.group(3).trim();
        return assertionSupport.compareValue(actual, assertionSupport.normalizeAssertionCondition(operator, "EQUALS"), expected).success();
    }

    private int resolveScenarioLoopCount(ApiScenarioStepInput step, Map<String, String> variables) {
        String loopType = normalizeLoopType(step.loopType());
        if ("FOREACH".equals(loopType)) {
            return scenarioForeachItems(step, variables).size();
        }
        if ("WHILE".equals(loopType)) {
            return evaluateScenarioCondition(step, variables) ? MAX_SCENARIO_LOOP_COUNT : 0;
        }
        int count = Optional.ofNullable(step.loopCount()).orElse(1);
        return Math.max(0, Math.min(MAX_SCENARIO_LOOP_COUNT, count));
    }

    private List<String> scenarioForeachItems(ApiScenarioStepInput step, Map<String, String> variables) {
        if (!"FOREACH".equals(normalizeLoopType(step.loopType()))) {
            return List.of();
        }
        String expression = Optional.ofNullable(variableResolver.replaceVariables(step.foreachExpression(), variables)).orElse("");
        return java.util.Arrays.stream(expression.split("[,\\n]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(MAX_SCENARIO_LOOP_COUNT)
                .toList();
    }

    private String normalizeLoopType(String loopType) {
        String normalized = Optional.ofNullable(loopType).orElse("FIXED").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WHILE", "FOREACH" -> normalized;
            default -> "FIXED";
        };
    }

    private ApiExecutionRuntimeModels.RunStepComputation syntheticScenarioStep(
            int stepOrder,
            String stepName,
            boolean success,
            long durationMs,
            String message,
            List<ApiProcessorResult> processorResults
    ) {
        return new ApiExecutionRuntimeModels.RunStepComputation(success, new ApiRunStepResultResponse(
                null,
                null,
                stepOrder,
                stepName,
                null,
                success,
                durationMs,
                null,
                null,
                List.of(),
                List.of(),
                defaultList(processorResults),
                success ? null : message,
                LocalDateTime.now()
        ));
    }

    private void sleep(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Wait processor was interrupted");
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }

    interface ScenarioExecutionDelegate {
        String normalizeScenarioStepType(ApiScenarioStepInput step);

        Long normalizeScenarioResourceId(ApiScenarioStepInput step);

        ApiDefinitionEntity requireDefinition(Long id);

        ApiDefinitionCaseEntity requireCase(Long id);

        ApiScenarioEntity requireScenario(Long id);

        void validateReadable(Long workspaceId, String workspaceCode, String message);

        List<ApiScenarioStepInput> readScenarioSteps(String json);

        ApiExecutionRuntimeModels.RunStepComputation executeDefinition(
                ApiDefinitionEntity definition,
                String stepName,
                int stepOrder,
                Map<String, String> variables,
                ApiExecutionRuntimeModels.ResolvedEnvironment environment
        );

        ApiExecutionRuntimeModels.RunStepComputation executeCase(
                ApiDefinitionCaseEntity apiCase,
                String stepName,
                int stepOrder,
                Map<String, String> variables,
                ApiExecutionRuntimeModels.ResolvedEnvironment environment
        );

        ApiExecutionRuntimeModels.RunStepComputation executeCustomRequestStep(
                ApiScenarioStepInput step,
                int stepOrder,
                Map<String, String> variables,
                ApiExecutionRuntimeModels.ResolvedEnvironment environment,
                Long workspaceId
        );
    }
}
