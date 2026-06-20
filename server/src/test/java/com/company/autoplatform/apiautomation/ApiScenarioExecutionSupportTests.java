package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApiScenarioExecutionSupportTests {

    private final ApiScenarioExecutionSupport scenarioSupport = new ApiScenarioExecutionSupport(
            new ApiAutomationScriptRunner(),
            new ApiVariableResolver(),
            new ApiAssertionEvaluator(new ApiAutomationScriptRunner(), new ApiAssertionSupport()),
            new ApiAssertionSupport()
    );

    @Test
    void scriptScenarioStepWritesVariablesForLaterSteps() {
        Map<String, String> variables = new HashMap<>();

        List<ApiExecutionRuntimeModels.RunStepComputation> results = scenarioSupport.executeScenarioSteps(
                List.of(scriptStep("Script", "setVar('token', 'alpha'); log('ok');")),
                new int[]{1},
                variables,
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 0, 0),
                new FakeDelegate()
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(variables).containsEntry("token", "alpha");
        assertThat(results.getFirst().response().processorResults().getFirst().outputVariables())
                .containsEntry("token", "alpha");
    }

    @Test
    void ifControllerExecutesOnlyWhenConditionMatches() {
        FakeDelegate delegate = new FakeDelegate();
        Map<String, String> variables = new HashMap<>();
        variables.put("enabled", "yes");

        List<ApiExecutionRuntimeModels.RunStepComputation> matched = scenarioSupport.executeScenarioSteps(
                List.of(ifStep("{{enabled}} == yes", List.of(apiStep(10L)))),
                new int[]{1},
                variables,
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 0, 0),
                delegate
        );
        assertThat(matched).hasSize(2);
        assertThat(delegate.executedDefinitions).isEqualTo(1);

        delegate.executedDefinitions = 0;
        variables.put("enabled", "no");
        List<ApiExecutionRuntimeModels.RunStepComputation> skipped = scenarioSupport.executeScenarioSteps(
                List.of(ifStep("{{enabled}} == yes", List.of(apiStep(10L)))),
                new int[]{1},
                variables,
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 0, 0),
                delegate
        );
        assertThat(skipped).hasSize(1);
        assertThat(skipped.getFirst().response().errorMessage()).isNull();
        assertThat(delegate.executedDefinitions).isZero();
    }

    @Test
    void foreachLoopKeepsItemAndLoopIndexBehavior() {
        FakeDelegate delegate = new FakeDelegate();
        Map<String, String> variables = new HashMap<>();

        List<ApiExecutionRuntimeModels.RunStepComputation> results = scenarioSupport.executeScenarioSteps(
                List.of(loopStep("FOREACH", null, "A,B,C", List.of(apiStep(10L)))),
                new int[]{1},
                variables,
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 0, 0),
                delegate
        );

        assertThat(results).hasSize(4);
        assertThat(delegate.executedDefinitions).isEqualTo(3);
        assertThat(variables).containsEntry("loopIndex", "2").containsEntry("item", "C");
    }

    @Test
    void referencedScenarioDepthLimitStillFails() {
        FakeDelegate delegate = new FakeDelegate();

        List<ApiExecutionRuntimeModels.RunStepComputation> results = scenarioSupport.executeScenarioSteps(
                List.of(referencedScenarioStep(2L)),
                new int[]{1},
                new HashMap<>(),
                null,
                "APP",
                1L,
                1L,
                3,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 0, 0),
                delegate
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isFalse();
        assertThat(results.getFirst().response().errorMessage()).contains("Scenario nesting depth exceeds 3");
    }

    @Test
    void retriesFailedStepBeforeContinuing() {
        FakeDelegate delegate = new FakeDelegate();
        delegate.definitionFailuresBeforeSuccess = 1;

        List<ApiExecutionRuntimeModels.RunStepComputation> results = scenarioSupport.executeScenarioSteps(
                List.of(apiStep(10L)),
                new int[]{1},
                new HashMap<>(),
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 2, 0),
                delegate
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().success()).isTrue();
        assertThat(delegate.executedDefinitions).isEqualTo(2);
        assertThat(results.getFirst().response().processorResults()).anyMatch(log -> log.message().contains("Retry 1/2"));
    }

    @Test
    void stopsWhenGlobalTimeoutIsExceededBeforeNextStep() {
        FakeDelegate delegate = new FakeDelegate();
        delegate.nextDurationMs = 1200L;

        List<ApiExecutionRuntimeModels.RunStepComputation> results = scenarioSupport.executeScenarioSteps(
                List.of(apiStep(10L), apiStep(20L)),
                new int[]{1},
                new HashMap<>(),
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(true, 1000, 0, 0),
                delegate
        );

        assertThat(results).hasSize(2);
        assertThat(delegate.executedDefinitions).isEqualTo(1);
        assertThat(results.get(1).success()).isFalse();
        assertThat(results.get(1).response().errorMessage()).contains("场景执行超过全局超时时间");
    }

    @Test
    void defaultStepWaitAppliesBetweenExecutableSteps() {
        FakeDelegate delegate = new FakeDelegate();

        List<ApiExecutionRuntimeModels.RunStepComputation> results = scenarioSupport.executeScenarioSteps(
                List.of(apiStep(10L), apiStep(20L)),
                new int[]{1},
                new HashMap<>(),
                null,
                "APP",
                1L,
                1L,
                0,
                new HashSet<>(),
                ApiScenarioExecutionSupport.ScenarioExecutionPolicy.of(false, 300000, 0, 1),
                delegate
        );

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().response().processorResults()).anyMatch(log -> log.message().contains("Default wait 1 ms"));
    }

    private ApiScenarioStepInput apiStep(Long resourceId) {
        return new ApiScenarioStepInput(null, "API step", "API", "DEFINITION", resourceId, true,
                null, List.of(), List.of(), List.of(), null, null, null, null, null, null, null, List.of());
    }

    private ApiScenarioStepInput scriptStep(String name, String script) {
        return new ApiScenarioStepInput(null, name, "SCRIPT", null, null, true,
                null, List.of(), List.of(), List.of(), null, null, null, null, null, null, script, List.of());
    }

    private ApiScenarioStepInput ifStep(String expression, List<ApiScenarioStepInput> children) {
        return new ApiScenarioStepInput(null, "IF", "IF_CONTROLLER", null, null, true,
                null, List.of(), List.of(), List.of(), null, "EXPRESSION", expression, null, null, null, null, children);
    }

    private ApiScenarioStepInput loopStep(String loopType, Integer loopCount, String foreachExpression, List<ApiScenarioStepInput> children) {
        return new ApiScenarioStepInput(null, "Loop", "LOOP_CONTROLLER", null, null, true,
                null, List.of(), List.of(), List.of(), null, null, null, loopType, loopCount, foreachExpression, null, children);
    }

    private ApiScenarioStepInput referencedScenarioStep(Long scenarioId) {
        return new ApiScenarioStepInput(null, "Ref", "API_SCENARIO", null, scenarioId, true,
                null, List.of(), List.of(), List.of(), null, null, null, null, null, null, null, List.of());
    }

    private static class FakeDelegate implements ApiScenarioExecutionSupport.ScenarioExecutionDelegate {
        int executedDefinitions;
        int definitionFailuresBeforeSuccess;
        long nextDurationMs;

        @Override
        public String normalizeScenarioStepType(ApiScenarioStepInput step) {
            return step.stepType();
        }

        @Override
        public Long normalizeScenarioResourceId(ApiScenarioStepInput step) {
            return step.resourceId();
        }

        @Override
        public ApiDefinitionEntity requireDefinition(Long id) {
            ApiDefinitionEntity entity = new ApiDefinitionEntity();
            entity.setId(id);
            entity.setWorkspaceId(1L);
            entity.setDefinitionName("Definition " + id);
            return entity;
        }

        @Override
        public ApiDefinitionCaseEntity requireCase(Long id) {
            ApiDefinitionCaseEntity entity = new ApiDefinitionCaseEntity();
            entity.setId(id);
            entity.setWorkspaceId(1L);
            entity.setCaseName("Case " + id);
            return entity;
        }

        @Override
        public ApiScenarioEntity requireScenario(Long id) {
            ApiScenarioEntity entity = new ApiScenarioEntity();
            entity.setId(id);
            entity.setWorkspaceId(1L);
            entity.setScenarioName("Scenario " + id);
            entity.setStepsJson("[]");
            return entity;
        }

        @Override
        public void validateReadable(Long workspaceId, String workspaceCode, String message) {
            if (!Long.valueOf(1L).equals(workspaceId)) {
                throw new NotFoundException(message);
            }
        }

        @Override
        public List<ApiScenarioStepInput> readScenarioSteps(String json) {
            return List.of();
        }

        @Override
        public ApiExecutionRuntimeModels.RunStepComputation executeDefinition(
                ApiDefinitionEntity definition,
                String stepName,
                int stepOrder,
                Map<String, String> variables,
                ApiExecutionRuntimeModels.ResolvedEnvironment environment
        ) {
            executedDefinitions++;
            if (definitionFailuresBeforeSuccess > 0) {
                definitionFailuresBeforeSuccess--;
                return failedStep(stepOrder, stepName, "Mock failure");
            }
            return successfulStep(stepOrder, stepName);
        }

        @Override
        public ApiExecutionRuntimeModels.RunStepComputation executeCase(
                ApiDefinitionCaseEntity apiCase,
                String stepName,
                int stepOrder,
                Map<String, String> variables,
                ApiExecutionRuntimeModels.ResolvedEnvironment environment
        ) {
            return successfulStep(stepOrder, stepName);
        }

        @Override
        public ApiExecutionRuntimeModels.RunStepComputation executeCustomRequestStep(
                ApiScenarioStepInput step,
                int stepOrder,
                Map<String, String> variables,
                ApiExecutionRuntimeModels.ResolvedEnvironment environment,
                Long workspaceId
        ) {
            return successfulStep(stepOrder, step.stepName());
        }

        private ApiExecutionRuntimeModels.RunStepComputation successfulStep(int stepOrder, String stepName) {
            long durationMs = nextDurationMs;
            nextDurationMs = 0L;
            return new ApiExecutionRuntimeModels.RunStepComputation(true, new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    null,
                    true,
                    durationMs,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    java.time.LocalDateTime.now()
            ));
        }

        private ApiExecutionRuntimeModels.RunStepComputation failedStep(int stepOrder, String stepName, String message) {
            return new ApiExecutionRuntimeModels.RunStepComputation(false, new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    null,
                    false,
                    0L,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    message,
                    java.time.LocalDateTime.now()
            ));
        }
    }
}
