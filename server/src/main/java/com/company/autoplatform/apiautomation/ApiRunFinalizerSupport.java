package com.company.autoplatform.apiautomation;

import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.execution.TaskEntity;
import com.company.autoplatform.execution.TaskMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.blankToNull;

@Component
public class ApiRunFinalizerSupport {

    private final ApiDefinitionMapper definitionMapper;
    private final ApiDefinitionCaseMapper caseMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final TaskMapper taskMapper;
    private final ReportMapper reportMapper;

    public ApiRunFinalizerSupport(
            ApiDefinitionMapper definitionMapper,
            ApiDefinitionCaseMapper caseMapper,
            ApiScenarioMapper scenarioMapper,
            TaskMapper taskMapper,
            ReportMapper reportMapper
    ) {
        this.definitionMapper = definitionMapper;
        this.caseMapper = caseMapper;
        this.scenarioMapper = scenarioMapper;
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
    }

    void finalizeRunDefinition(
            ApiDefinitionEntity definition,
            boolean success,
            TaskEntity task,
            ReportEntity report,
            ApiExecutionRuntimeModels.RunStepComputation step
    ) {
        String result = result(success);
        definition.setLastRunResult(result);
        definition.setLastRunAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        finalizeRunTaskAndReport(task, report, result, step.response().errorMessage());
    }

    void finalizeRunCase(
            ApiDefinitionCaseEntity apiCase,
            boolean success,
            TaskEntity task,
            ReportEntity report,
            ApiExecutionRuntimeModels.RunStepComputation step
    ) {
        String result = result(success);
        apiCase.setLastRunResult(result);
        apiCase.setLastRunAt(LocalDateTime.now());
        apiCase.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(apiCase);
        finalizeRunTaskAndReport(task, report, result, step.response().errorMessage());
    }

    void finalizeRunScenario(
            ApiScenarioEntity scenario,
            boolean success,
            String failureSummary,
            TaskEntity task,
            ReportEntity report
    ) {
        String result = result(success);
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

    private String result(boolean success) {
        return success ? "SUCCESS" : "FAILED";
    }
}
