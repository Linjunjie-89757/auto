package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.PageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Service
public class ApiAutomationService {

    private final ApiDefinitionDomainService definitionDomainService;
    private final ApiCaseDomainService caseDomainService;
    private final ApiScenarioDomainService scenarioDomainService;
    private final ApiConfigDomainService configDomainService;
    private final ApiRunHistoryDomainService runHistoryDomainService;
    private final ObjectProvider<ApiExecutionDomainService> executionDomainServiceProvider;

    public ApiAutomationService(
            ApiDefinitionDomainService definitionDomainService,
            ApiCaseDomainService caseDomainService,
            ApiScenarioDomainService scenarioDomainService,
            ApiConfigDomainService configDomainService,
            ApiRunHistoryDomainService runHistoryDomainService,
            ObjectProvider<ApiExecutionDomainService> executionDomainServiceProvider
    ) {
        this.definitionDomainService = definitionDomainService;
        this.caseDomainService = caseDomainService;
        this.scenarioDomainService = scenarioDomainService;
        this.configDomainService = configDomainService;
        this.runHistoryDomainService = runHistoryDomainService;
        this.executionDomainServiceProvider = executionDomainServiceProvider;
    }

    public PageResponse<ApiDefinitionItem> listDefinitions(
            String workspaceCode,
            String keyword,
            Long moduleId,
            Integer pageNo,
            Integer pageSize
    ) {
        return definitionDomainService.listDefinitions(workspaceCode, keyword, moduleId, pageNo, pageSize);
    }

    public ApiDefinitionDetail getDefinition(Long id, String workspaceCode) {
        return definitionDomainService.getDefinition(id, workspaceCode);
    }

    public PageResponse<ApiDefinitionCaseItem> listCases(
            String workspaceCode,
            Long definitionId,
            String keyword,
            Integer pageNo,
            Integer pageSize
    ) {
        return caseDomainService.listCases(workspaceCode, definitionId, keyword, pageNo, pageSize);
    }

    public ApiDefinitionCaseDetail getCase(Long id, String workspaceCode) {
        return caseDomainService.getCase(id, workspaceCode);
    }

    public PageResponse<ApiDefinitionCaseChangeHistoryItem> listCaseChangeHistory(Long caseId, String workspaceCode) {
        return runHistoryDomainService.listCaseChangeHistory(caseId, workspaceCode);
    }

    public PageResponse<ApiDefinitionCaseRunHistoryItem> listCaseRunHistory(Long caseId, String workspaceCode) {
        return runHistoryDomainService.listCaseRunHistory(caseId, workspaceCode);
    }

    public ApiDefinitionCaseRunHistoryDetail getCaseRunHistoryDetail(Long historyId, String workspaceCode) {
        return runHistoryDomainService.getCaseRunHistoryDetail(historyId, workspaceCode);
    }

    public ApiDefinitionDetail createDefinition(String headerWorkspaceCode, SaveApiDefinitionRequest request) {
        return definitionDomainService.createDefinition(headerWorkspaceCode, request);
    }

    public ApiDefinitionDetail updateDefinition(Long id, String headerWorkspaceCode, SaveApiDefinitionRequest request) {
        return definitionDomainService.updateDefinition(id, headerWorkspaceCode, request);
    }

    public ApiDefinitionCaseDetail createCase(String headerWorkspaceCode, SaveApiDefinitionCaseRequest request) {
        return caseDomainService.createCase(headerWorkspaceCode, request);
    }

    public ApiDefinitionCaseDetail updateCase(Long id, String headerWorkspaceCode, SaveApiDefinitionCaseRequest request) {
        return caseDomainService.updateCase(id, headerWorkspaceCode, request);
    }

    public void deleteDefinition(Long id, String workspaceCode) {
        definitionDomainService.deleteDefinition(id, workspaceCode);
    }

    public void deleteCase(Long id, String workspaceCode) {
        caseDomainService.deleteCase(id, workspaceCode);
    }

    public List<ApiDefinitionModuleItem> listDefinitionModules(String workspaceCode) {
        return definitionDomainService.listDefinitionModules(workspaceCode);
    }

    public ApiDefinitionModuleItem createDefinitionModule(String headerWorkspaceCode, ApiDefinitionModuleRequest request) {
        return definitionDomainService.createDefinitionModule(headerWorkspaceCode, request);
    }

    public ApiDefinitionModuleItem updateDefinitionModule(Long id, String workspaceCode, ApiDefinitionModuleRequest request) {
        return definitionDomainService.updateDefinitionModule(id, workspaceCode, request);
    }

    public ApiDefinitionModuleItem moveDefinitionModule(Long id, String workspaceCode, MoveApiDefinitionModuleRequest request) {
        return definitionDomainService.moveDefinitionModule(id, workspaceCode, request);
    }

    public void deleteDefinitionModule(Long id, String workspaceCode) {
        definitionDomainService.deleteDefinitionModule(id, workspaceCode);
    }

    public PageResponse<ApiScenarioItem> listScenarios(
            String workspaceCode,
            Long moduleId,
            String keyword,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        return scenarioDomainService.listScenarios(workspaceCode, moduleId, keyword, status, pageNo, pageSize);
    }

    public List<ApiScenarioModuleItem> listScenarioModules(String workspaceCode) {
        return scenarioDomainService.listScenarioModules(workspaceCode);
    }

    public ApiScenarioModuleItem createScenarioModule(String headerWorkspaceCode, ApiScenarioModuleRequest request) {
        return scenarioDomainService.createScenarioModule(headerWorkspaceCode, request);
    }

    public ApiScenarioModuleItem updateScenarioModule(Long id, String workspaceCode, ApiScenarioModuleRequest request) {
        return scenarioDomainService.updateScenarioModule(id, workspaceCode, request);
    }

    public ApiScenarioModuleItem moveScenarioModule(Long id, String workspaceCode, MoveApiScenarioModuleRequest request) {
        return scenarioDomainService.moveScenarioModule(id, workspaceCode, request);
    }

    public void deleteScenarioModule(Long id, String workspaceCode) {
        scenarioDomainService.deleteScenarioModule(id, workspaceCode);
    }

    public ApiScenarioDetail getScenario(Long id, String workspaceCode) {
        return scenarioDomainService.getScenario(id, workspaceCode);
    }

    public ApiScenarioDetail createScenario(String headerWorkspaceCode, SaveApiScenarioRequest request) {
        return scenarioDomainService.createScenario(headerWorkspaceCode, request);
    }

    public ApiScenarioDetail updateScenario(Long id, String headerWorkspaceCode, SaveApiScenarioRequest request) {
        return scenarioDomainService.updateScenario(id, headerWorkspaceCode, request);
    }

    public void deleteScenario(Long id, String workspaceCode) {
        scenarioDomainService.deleteScenario(id, workspaceCode);
    }

    public PageResponse<ApiEnvironmentItem> listEnvironments(String workspaceCode) {
        return configDomainService.listEnvironments(workspaceCode);
    }

    public ApiEnvironmentItem createEnvironment(String headerWorkspaceCode, ApiEnvironmentRequest request) {
        return configDomainService.createEnvironment(headerWorkspaceCode, request);
    }

    public ApiEnvironmentItem updateEnvironment(Long id, String headerWorkspaceCode, ApiEnvironmentRequest request) {
        return configDomainService.updateEnvironment(id, headerWorkspaceCode, request);
    }

    public void deleteEnvironment(Long id, String workspaceCode) {
        configDomainService.deleteEnvironment(id, workspaceCode);
    }

    public PageResponse<ApiVariableSetItem> listVariableSets(String workspaceCode) {
        return configDomainService.listVariableSets(workspaceCode);
    }

    public ApiVariableSetItem createVariableSet(String headerWorkspaceCode, ApiVariableSetRequest request) {
        return configDomainService.createVariableSet(headerWorkspaceCode, request);
    }

    public ApiVariableSetItem updateVariableSet(Long id, String headerWorkspaceCode, ApiVariableSetRequest request) {
        return configDomainService.updateVariableSet(id, headerWorkspaceCode, request);
    }

    public void deleteVariableSet(Long id, String workspaceCode) {
        configDomainService.deleteVariableSet(id, workspaceCode);
    }

    public ApiRunResponse debugRunDefinition(Long id, String workspaceCode, ApiRunRequest request) {
        return executionDomainServiceProvider.getObject().debugRunDefinition(id, workspaceCode, request);
    }

    public ApiRunResponse runCase(Long id, String workspaceCode, ApiRunRequest request) {
        return executionDomainServiceProvider.getObject().runCase(id, workspaceCode, request);
    }

    public ApiRunResponse debugRunDefinitionDraft(String workspaceCode, ApiDebugDefinitionRequest request) {
        return executionDomainServiceProvider.getObject().debugRunDefinitionDraft(workspaceCode, request);
    }

    public ApiRunResponse debugRunCaseDraft(String workspaceCode, ApiDebugCaseRequest request) {
        return executionDomainServiceProvider.getObject().debugRunCaseDraft(workspaceCode, request);
    }

    public ApiRunResponse runScenario(Long id, String workspaceCode, ApiRunRequest request) {
        return executionDomainServiceProvider.getObject().runScenario(id, workspaceCode, request);
    }

    public List<ApiRunStepResultResponse> listReportSteps(Long reportId, String workspaceCode) {
        return runHistoryDomainService.listReportSteps(reportId, workspaceCode);
    }
}

