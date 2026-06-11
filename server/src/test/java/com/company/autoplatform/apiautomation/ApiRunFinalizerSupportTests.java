package com.company.autoplatform.apiautomation;

import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.execution.TaskEntity;
import com.company.autoplatform.execution.TaskMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static com.company.autoplatform.apiautomation.ApiExecutionRuntimeModelFixtures.runStepComputation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiRunFinalizerSupportTests {

    private final ApiDefinitionMapper definitionMapper = mock(ApiDefinitionMapper.class);
    private final ApiDefinitionCaseMapper caseMapper = mock(ApiDefinitionCaseMapper.class);
    private final ApiScenarioMapper scenarioMapper = mock(ApiScenarioMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final ApiRunFinalizerSupport support = new ApiRunFinalizerSupport(
            definitionMapper,
            caseMapper,
            scenarioMapper,
            taskMapper,
            reportMapper
    );

    @Test
    void finalizesDefinitionSuccessStatus() {
        ApiDefinitionEntity definition = new ApiDefinitionEntity();
        TaskEntity task = new TaskEntity();
        ReportEntity report = new ReportEntity();

        support.finalizeRunDefinition(definition, true, task, report, step(true, null));

        assertThat(definition.getLastRunResult()).isEqualTo("SUCCESS");
        assertThat(definition.getLastRunAt()).isNotNull();
        assertThat(definition.getUpdatedAt()).isNotNull();
        assertThat(task.getTaskStatus()).isEqualTo("SUCCESS");
        assertThat(report.getResult()).isEqualTo("SUCCESS");
        assertThat(report.getFailureSummary()).isNull();
        verify(definitionMapper).updateById(definition);
        verify(taskMapper).updateById(task);
        verify(reportMapper).updateById(report);
    }

    @Test
    void finalizesDefinitionFailureStatus() {
        ApiDefinitionEntity definition = new ApiDefinitionEntity();
        TaskEntity task = new TaskEntity();
        ReportEntity report = new ReportEntity();

        support.finalizeRunDefinition(definition, false, task, report, step(false, "status mismatch"));

        assertThat(definition.getLastRunResult()).isEqualTo("FAILED");
        assertThat(task.getTaskStatus()).isEqualTo("FAILED");
        assertThat(report.getResult()).isEqualTo("FAILED");
        assertThat(report.getFailureSummary()).isEqualTo("status mismatch");
        verify(definitionMapper).updateById(definition);
        verify(taskMapper).updateById(task);
        verify(reportMapper).updateById(report);
    }

    @Test
    void finalizesCaseSuccessAndFailureStatus() {
        ApiDefinitionCaseEntity successCase = new ApiDefinitionCaseEntity();
        TaskEntity successTask = new TaskEntity();
        ReportEntity successReport = new ReportEntity();
        support.finalizeRunCase(successCase, true, successTask, successReport, step(true, null));

        assertThat(successCase.getLastRunResult()).isEqualTo("SUCCESS");
        assertThat(successCase.getLastRunAt()).isNotNull();
        assertThat(successCase.getUpdatedAt()).isNotNull();
        assertThat(successTask.getTaskStatus()).isEqualTo("SUCCESS");
        assertThat(successReport.getResult()).isEqualTo("SUCCESS");
        assertThat(successReport.getFailureSummary()).isNull();

        ApiDefinitionCaseEntity failedCase = new ApiDefinitionCaseEntity();
        TaskEntity failedTask = new TaskEntity();
        ReportEntity failedReport = new ReportEntity();
        support.finalizeRunCase(failedCase, false, failedTask, failedReport, step(false, "case failed"));

        assertThat(failedCase.getLastRunResult()).isEqualTo("FAILED");
        assertThat(failedTask.getTaskStatus()).isEqualTo("FAILED");
        assertThat(failedReport.getResult()).isEqualTo("FAILED");
        assertThat(failedReport.getFailureSummary()).isEqualTo("case failed");
        verify(caseMapper).updateById(successCase);
        verify(caseMapper).updateById(failedCase);
        verify(taskMapper).updateById(successTask);
        verify(taskMapper).updateById(failedTask);
        verify(reportMapper).updateById(successReport);
        verify(reportMapper).updateById(failedReport);
    }

    @Test
    void finalizesScenarioFailureSummaryAndBlankSummaryAsNull() {
        ApiScenarioEntity scenario = new ApiScenarioEntity();
        TaskEntity task = new TaskEntity();
        ReportEntity report = new ReportEntity();

        support.finalizeRunScenario(scenario, false, " assertion failed ", task, report);

        assertThat(scenario.getLastRunResult()).isEqualTo("FAILED");
        assertThat(scenario.getLastRunAt()).isNotNull();
        assertThat(scenario.getUpdatedAt()).isNotNull();
        assertThat(task.getTaskStatus()).isEqualTo("FAILED");
        assertThat(report.getResult()).isEqualTo("FAILED");
        assertThat(report.getFailureSummary()).isEqualTo("assertion failed");
        verify(scenarioMapper).updateById(scenario);
        verify(taskMapper).updateById(task);
        verify(reportMapper).updateById(report);

        TaskEntity blankTask = new TaskEntity();
        ReportEntity blankReport = new ReportEntity();
        support.finalizeRunTaskAndReport(blankTask, blankReport, "FAILED", "   ");

        assertThat(blankTask.getTaskStatus()).isEqualTo("FAILED");
        assertThat(blankReport.getResult()).isEqualTo("FAILED");
        assertThat(blankReport.getFailureSummary()).isNull();
        assertThat(blankTask.getUpdatedAt()).isNotNull();
        assertThat(blankReport.getUpdatedAt()).isNotNull();
        verify(taskMapper).updateById(blankTask);
        verify(reportMapper).updateById(blankReport);
    }

    private ApiExecutionRuntimeModels.RunStepComputation step(boolean success, String errorMessage) {
        return runStepComputation(success, new ApiRunStepResultResponse(
                null,
                null,
                1,
                "Step",
                10L,
                success,
                12L,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                errorMessage,
                LocalDateTime.now()
        ));
    }
}
