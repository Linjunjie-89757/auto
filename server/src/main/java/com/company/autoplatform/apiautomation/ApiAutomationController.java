package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.ApiResponse;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceScope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@RestController
@RequestMapping("/api/automation/api")
public class ApiAutomationController {

    private final ApiAutomationService apiAutomationService;

    public ApiAutomationController(ApiAutomationService apiAutomationService) {
        this.apiAutomationService = apiAutomationService;
    }

    @GetMapping("/definitions")
    public ApiResponse<PageResponse<ApiDefinitionItem>> listDefinitions(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.listDefinitions(workspaceCode));
    }

    @GetMapping("/definitions/{id}")
    public ApiResponse<ApiDefinitionDetail> getDefinition(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.getDefinition(id, workspaceCode));
    }

    @PostMapping("/definitions")
    public ApiResponse<ApiDefinitionDetail> createDefinition(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveApiDefinitionRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.createDefinition(workspaceCode, request), "API definition created");
    }

    @PutMapping("/definitions/{id}")
    public ApiResponse<ApiDefinitionDetail> updateDefinition(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveApiDefinitionRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.updateDefinition(id, workspaceCode, request), "API definition updated");
    }

    @DeleteMapping("/definitions/{id}")
    public ApiResponse<Void> deleteDefinition(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        apiAutomationService.deleteDefinition(id, workspaceCode);
        return ApiResponse.ok(null, "API definition deleted");
    }

    @PostMapping("/definitions/{id}/debug-run")
    public ApiResponse<ApiRunResponse> debugRunDefinition(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestBody(required = false) ApiRunRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.debugRunDefinition(id, workspaceCode,
                request == null ? new ApiRunRequest(null, null, null) : request));
    }

    @PostMapping("/definitions/debug-run")
    public ApiResponse<ApiRunResponse> debugRunDefinitionDraft(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ApiDebugDefinitionRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.debugRunDefinitionDraft(workspaceCode, request));
    }

    @GetMapping("/cases")
    public ApiResponse<PageResponse<ApiDefinitionCaseItem>> listCases(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(apiAutomationService.listCases(workspaceCode, definitionId, keyword));
    }

    @GetMapping("/cases/{id}")
    public ApiResponse<ApiDefinitionCaseDetail> getCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.getCase(id, workspaceCode));
    }

    @PostMapping("/cases")
    public ApiResponse<ApiDefinitionCaseDetail> createCase(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveApiDefinitionCaseRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.createCase(workspaceCode, request), "API case created");
    }

    @PutMapping("/cases/{id}")
    public ApiResponse<ApiDefinitionCaseDetail> updateCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveApiDefinitionCaseRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.updateCase(id, workspaceCode, request), "API case updated");
    }

    @DeleteMapping("/cases/{id}")
    public ApiResponse<Void> deleteCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        apiAutomationService.deleteCase(id, workspaceCode);
        return ApiResponse.ok(null, "API case deleted");
    }

    @PostMapping("/cases/{id}/run")
    public ApiResponse<ApiRunResponse> runCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestBody(required = false) ApiRunRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.runCase(id, workspaceCode,
                request == null ? new ApiRunRequest(null, null, null) : request));
    }

    @PostMapping("/cases/debug-run")
    public ApiResponse<ApiRunResponse> debugRunCaseDraft(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ApiDebugCaseRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.debugRunCaseDraft(workspaceCode, request));
    }

    @GetMapping("/scenarios")
    public ApiResponse<PageResponse<ApiScenarioItem>> listScenarios(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.listScenarios(workspaceCode));
    }

    @GetMapping("/scenarios/{id}")
    public ApiResponse<ApiScenarioDetail> getScenario(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.getScenario(id, workspaceCode));
    }

    @PostMapping("/scenarios")
    public ApiResponse<ApiScenarioDetail> createScenario(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveApiScenarioRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.createScenario(workspaceCode, request), "API scenario created");
    }

    @PutMapping("/scenarios/{id}")
    public ApiResponse<ApiScenarioDetail> updateScenario(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveApiScenarioRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.updateScenario(id, workspaceCode, request), "API scenario updated");
    }

    @DeleteMapping("/scenarios/{id}")
    public ApiResponse<Void> deleteScenario(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        apiAutomationService.deleteScenario(id, workspaceCode);
        return ApiResponse.ok(null, "API scenario deleted");
    }

    @PostMapping("/scenarios/{id}/run")
    public ApiResponse<ApiRunResponse> runScenario(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestBody(required = false) ApiRunRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.runScenario(id, workspaceCode,
                request == null ? new ApiRunRequest(null, null, null) : request));
    }

    @GetMapping("/environments")
    public ApiResponse<PageResponse<ApiEnvironmentItem>> listEnvironments(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.listEnvironments(workspaceCode));
    }

    @PostMapping("/environments")
    public ApiResponse<ApiEnvironmentItem> createEnvironment(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ApiEnvironmentRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.createEnvironment(workspaceCode, request), "Environment created");
    }

    @PutMapping("/environments/{id}")
    public ApiResponse<ApiEnvironmentItem> updateEnvironment(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ApiEnvironmentRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.updateEnvironment(id, workspaceCode, request), "Environment updated");
    }

    @DeleteMapping("/environments/{id}")
    public ApiResponse<Void> deleteEnvironment(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        apiAutomationService.deleteEnvironment(id, workspaceCode);
        return ApiResponse.ok(null, "Environment deleted");
    }

    @GetMapping("/variable-sets")
    public ApiResponse<PageResponse<ApiVariableSetItem>> listVariableSets(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.listVariableSets(workspaceCode));
    }

    @PostMapping("/variable-sets")
    public ApiResponse<ApiVariableSetItem> createVariableSet(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ApiVariableSetRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.createVariableSet(workspaceCode, request), "Variable set created");
    }

    @PutMapping("/variable-sets/{id}")
    public ApiResponse<ApiVariableSetItem> updateVariableSet(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ApiVariableSetRequest request
    ) {
        return ApiResponse.ok(apiAutomationService.updateVariableSet(id, workspaceCode, request), "Variable set updated");
    }

    @DeleteMapping("/variable-sets/{id}")
    public ApiResponse<Void> deleteVariableSet(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        apiAutomationService.deleteVariableSet(id, workspaceCode);
        return ApiResponse.ok(null, "Variable set deleted");
    }

    @GetMapping("/runs/reports/{id}/steps")
    public ApiResponse<List<ApiRunStepResultResponse>> listReportSteps(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(apiAutomationService.listReportSteps(id, workspaceCode));
    }
}
