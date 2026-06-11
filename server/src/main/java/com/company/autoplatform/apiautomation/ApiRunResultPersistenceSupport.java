package com.company.autoplatform.apiautomation;

import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.settings.EnvConfigEntity;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetEntity;
import com.company.autoplatform.settings.ParamSetMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.blankToNull;

@Component
public class ApiRunResultPersistenceSupport {

    private static final String API_ENV_TYPE = "API";
    private static final String API_VARIABLE_SET_TYPE = "API_VARIABLE_SET";

    private final ApiDefinitionCaseRunHistoryMapper caseRunHistoryMapper;
    private final ApiRunStepResultMapper runStepResultMapper;
    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final ReportMapper reportMapper;

    public ApiRunResultPersistenceSupport(
            ApiDefinitionCaseRunHistoryMapper caseRunHistoryMapper,
            ApiRunStepResultMapper runStepResultMapper,
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper,
            ReportMapper reportMapper
    ) {
        this.caseRunHistoryMapper = caseRunHistoryMapper;
        this.runStepResultMapper = runStepResultMapper;
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.reportMapper = reportMapper;
    }

    void persistStep(
            ReportEntity report,
            Long workspaceId,
            ApiExecutionRuntimeModels.RunStepComputation computation
    ) {
        runStepResultMapper.insert(toRunStepResultEntity(report, workspaceId, computation));
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    void persistCaseRunHistory(
            ApiDefinitionCaseEntity apiCase,
            ReportEntity report,
            ApiExecutionRuntimeModels.RunStepComputation step,
            Long environmentId,
            Long variableSetId
    ) {
        caseRunHistoryMapper.insert(toCaseRunHistoryEntity(apiCase, report, step, environmentId, variableSetId));
    }

    ApiRunStepResultEntity toRunStepResultEntity(
            ReportEntity report,
            Long workspaceId,
            ApiExecutionRuntimeModels.RunStepComputation computation
    ) {
        ApiAutomationModels.ApiRunStepResultResponse response = computation.response();
        ApiRunStepResultEntity entity = new ApiRunStepResultEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setReportId(report.getId());
        entity.setStepOrder(response.stepOrder());
        entity.setStepName(response.stepName());
        entity.setDefinitionId(response.definitionId());
        entity.setSuccess(response.success());
        entity.setDurationMs(response.durationMs());
        entity.setRequestSnapshotJson(ApiAutomationJsonSupport.toJson(response.request(), "Failed to serialize request snapshot"));
        entity.setResponseSnapshotJson(ApiAutomationJsonSupport.toJson(response.response(), "Failed to serialize response snapshot"));
        entity.setAssertionResultsJson(ApiAutomationJsonSupport.toJson(response.assertionResults(), "Failed to serialize assertion results"));
        entity.setExtractionResultsJson(ApiAutomationJsonSupport.toJson(response.extractionResults(), "Failed to serialize extraction results"));
        entity.setProcessorResultsJson(ApiAutomationJsonSupport.toJson(response.processorResults(), "Failed to serialize processor results"));
        entity.setErrorMessage(response.errorMessage());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    ApiDefinitionCaseRunHistoryEntity toCaseRunHistoryEntity(
            ApiDefinitionCaseEntity apiCase,
            ReportEntity report,
            ApiExecutionRuntimeModels.RunStepComputation step,
            Long environmentId,
            Long variableSetId
    ) {
        ApiAutomationModels.ApiRunStepResultResponse response = step.response();
        ApiDefinitionCaseRunHistoryEntity entity = new ApiDefinitionCaseRunHistoryEntity();
        entity.setWorkspaceId(apiCase.getWorkspaceId());
        entity.setDefinitionId(apiCase.getDefinitionId());
        entity.setCaseId(apiCase.getId());
        entity.setReportId(report.getId());
        entity.setCaseName(apiCase.getCaseName());
        entity.setRunResult(report.getResult());
        entity.setFailureSummary(blankToNull(report.getFailureSummary()));
        entity.setOperatorName(CurrentUserContext.require().displayName());
        entity.setEnvironmentId(environmentId);
        entity.setEnvironmentName(resolveEnvironmentName(environmentId));
        entity.setVariableSetId(variableSetId);
        entity.setVariableSetName(resolveVariableSetName(variableSetId));
        entity.setStatusCode(response.response() == null ? null : response.response().statusCode());
        entity.setDurationMs(response.durationMs());
        entity.setResponseSize(computeResponseSize(response.response()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private String resolveEnvironmentName(Long environmentId) {
        if (environmentId == null) {
            return null;
        }
        EnvConfigEntity environment = envConfigMapper.selectById(environmentId);
        if (environment == null || !API_ENV_TYPE.equals(environment.getEnvType())) {
            throw new NotFoundException("Environment not found");
        }
        return environment.getEnvName();
    }

    private String resolveVariableSetName(Long variableSetId) {
        if (variableSetId == null) {
            return null;
        }
        ParamSetEntity variableSet = paramSetMapper.selectById(variableSetId);
        if (variableSet == null || !API_VARIABLE_SET_TYPE.equals(variableSet.getParamType())) {
            throw new NotFoundException("Variable set not found");
        }
        return variableSet.getParamName();
    }

    private Long computeResponseSize(ApiAutomationModels.ApiResponseSnapshot response) {
        if (response == null || response.body() == null) {
            return 0L;
        }
        return (long) response.body().getBytes(StandardCharsets.UTF_8).length;
    }
}
