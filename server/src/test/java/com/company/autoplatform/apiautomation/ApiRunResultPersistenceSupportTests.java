package com.company.autoplatform.apiautomation;

import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.settings.EnvConfigEntity;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetEntity;
import com.company.autoplatform.settings.ParamSetMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static com.company.autoplatform.apiautomation.ApiExecutionRuntimeModelFixtures.runStepComputation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRunResultPersistenceSupportTests {

    private final ApiDefinitionCaseRunHistoryMapper caseRunHistoryMapper = mock(ApiDefinitionCaseRunHistoryMapper.class);
    private final ApiRunStepResultMapper runStepResultMapper = mock(ApiRunStepResultMapper.class);
    private final EnvConfigMapper envConfigMapper = mock(EnvConfigMapper.class);
    private final ParamSetMapper paramSetMapper = mock(ParamSetMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final ApiRunResultPersistenceSupport support = new ApiRunResultPersistenceSupport(
            caseRunHistoryMapper,
            runStepResultMapper,
            envConfigMapper,
            paramSetMapper,
            reportMapper
    );

    @AfterEach
    void clearCurrentUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertsStepResponseToEntityWithSnapshotsAndResultJson() {
        ReportEntity report = report(20L, "SUCCESS", null);
        ApiExecutionRuntimeModels.RunStepComputation computation = stepComputation(
                true,
                responseSnapshot(200, "{\"ok\":true}"),
                List.of(new ApiAssertionResult("a1", "RESPONSE_CODE", "Status", "statusCode", "EQUALS", "200", "200", true, "Assertion passed")),
                List.of(new ApiExtractionResult("token", true, "abc", null)),
                List.of(new ApiProcessorResult("POST", "EXTRACT", "Extract token", true, 5L, "ok", List.of("done"), Map.of("token", "abc")))
        );

        ApiRunStepResultEntity entity = support.toRunStepResultEntity(report, 10L, computation);

        assertThat(entity.getWorkspaceId()).isEqualTo(10L);
        assertThat(entity.getReportId()).isEqualTo(20L);
        assertThat(entity.getStepOrder()).isEqualTo(1);
        assertThat(entity.getStepName()).isEqualTo("Login");
        assertThat(entity.getDefinitionId()).isEqualTo(30L);
        assertThat(entity.getSuccess()).isTrue();
        assertThat(entity.getDurationMs()).isEqualTo(123L);
        assertThat(entity.getRequestSnapshotJson()).contains("\"method\":\"GET\"", "\"url\":\"/login\"");
        assertThat(entity.getResponseSnapshotJson()).contains("\"statusCode\":200", "\\\"ok\\\":true");
        assertThat(entity.getAssertionResultsJson()).contains("RESPONSE_CODE", "Assertion passed");
        assertThat(entity.getExtractionResultsJson()).contains("token", "abc");
        assertThat(entity.getProcessorResultsJson()).contains("POST", "Extract token");
        assertThat(entity.getErrorMessage()).isNull();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void persistStepInsertsResultAndUpdatesReportTimestamp() {
        ReportEntity report = report(20L, "SUCCESS", null);
        LocalDateTime before = LocalDateTime.now();

        support.persistStep(report, 10L, stepComputation(true, responseSnapshot(200, "ok"), List.of(), List.of(), List.of()));

        ArgumentCaptor<ApiRunStepResultEntity> stepCaptor = ArgumentCaptor.forClass(ApiRunStepResultEntity.class);
        verify(runStepResultMapper).insert(stepCaptor.capture());
        verify(reportMapper).updateById(report);
        assertThat(stepCaptor.getValue().getReportId()).isEqualTo(20L);
        assertThat(report.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void convertsCaseRunHistoryWithResultFailureSummaryAndCurrentOperator() {
        setCurrentUser();
        EnvConfigEntity environment = new EnvConfigEntity();
        environment.setEnvType("API");
        environment.setEnvName("Test Env");
        when(envConfigMapper.selectById(100L)).thenReturn(environment);
        ParamSetEntity variableSet = new ParamSetEntity();
        variableSet.setParamType("API_VARIABLE_SET");
        variableSet.setParamName("Smoke Vars");
        when(paramSetMapper.selectById(200L)).thenReturn(variableSet);
        ApiDefinitionCaseEntity apiCase = new ApiDefinitionCaseEntity();
        apiCase.setId(1L);
        apiCase.setWorkspaceId(10L);
        apiCase.setDefinitionId(30L);
        apiCase.setCaseName("Login case");
        ReportEntity report = report(20L, "FAILED", " assertion failed ");

        ApiDefinitionCaseRunHistoryEntity entity = support.toCaseRunHistoryEntity(
                apiCase,
                report,
                stepComputation(false, responseSnapshot(500, "错误"), List.of(), List.of(), List.of()),
                100L,
                200L
        );

        assertThat(entity.getWorkspaceId()).isEqualTo(10L);
        assertThat(entity.getDefinitionId()).isEqualTo(30L);
        assertThat(entity.getCaseId()).isEqualTo(1L);
        assertThat(entity.getReportId()).isEqualTo(20L);
        assertThat(entity.getCaseName()).isEqualTo("Login case");
        assertThat(entity.getRunResult()).isEqualTo("FAILED");
        assertThat(entity.getFailureSummary()).isEqualTo("assertion failed");
        assertThat(entity.getOperatorName()).isEqualTo("Zhang Li");
        assertThat(entity.getEnvironmentId()).isEqualTo(100L);
        assertThat(entity.getEnvironmentName()).isEqualTo("Test Env");
        assertThat(entity.getVariableSetId()).isEqualTo(200L);
        assertThat(entity.getVariableSetName()).isEqualTo("Smoke Vars");
        assertThat(entity.getStatusCode()).isEqualTo(500);
        assertThat(entity.getDurationMs()).isEqualTo(123L);
        assertThat(entity.getResponseSize()).isEqualTo(6L);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    private void setCurrentUser() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                11L,
                "zhangli",
                "Zhang Li",
                "{noop}123456",
                PlatformRole.PLATFORM_ADMIN,
                1
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities())
        );
    }

    private ReportEntity report(Long id, String result, String failureSummary) {
        ReportEntity report = new ReportEntity();
        report.setId(id);
        report.setResult(result);
        report.setFailureSummary(failureSummary);
        return report;
    }

    private ApiExecutionRuntimeModels.RunStepComputation stepComputation(
            boolean success,
            ApiResponseSnapshot response,
            List<ApiAssertionResult> assertionResults,
            List<ApiExtractionResult> extractionResults,
            List<ApiProcessorResult> processorResults
    ) {
        return runStepComputation(success, new ApiRunStepResultResponse(
                null,
                null,
                1,
                "Login",
                30L,
                success,
                123L,
                new ApiRequestSnapshot("GET", "/login", Map.of("Accept", "application/json"), List.of(), List.of(),
                        "NONE", null, List.of(), null, null, null),
                response,
                assertionResults,
                extractionResults,
                processorResults,
                success ? null : "failed",
                LocalDateTime.now()
        ));
    }

    private ApiResponseSnapshot responseSnapshot(Integer statusCode, String body) {
        return new ApiResponseSnapshot(statusCode, Map.of("content-type", "application/json"), body, "application/json");
    }
}
