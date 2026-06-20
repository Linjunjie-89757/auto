package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.ApiResponse;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceScope;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;

import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.*;

@RestController
@RequestMapping("/api/automation/web")
public class WebUiAutomationController {

    private final WebUiAutomationService webUiAutomationService;

    public WebUiAutomationController(WebUiAutomationService webUiAutomationService) {
        this.webUiAutomationService = webUiAutomationService;
    }

    @GetMapping("/cases")
    public ApiResponse<PageResponse<WebUiCaseItem>> listCases(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.ok(webUiAutomationService.listCases(workspaceCode, keyword, moduleName, status, pageNo, pageSize));
    }

    @GetMapping("/cases/{id}")
    public ApiResponse<WebUiCaseDetail> getCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.getCase(id, workspaceCode));
    }

    @PostMapping("/cases")
    public ApiResponse<WebUiCaseDetail> createCase(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiCaseRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.createCase(workspaceCode, request), "Web UI case created");
    }

    @PutMapping("/cases/{id}")
    public ApiResponse<WebUiCaseDetail> updateCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiCaseRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.updateCase(id, workspaceCode, request), "Web UI case updated");
    }

    @DeleteMapping("/cases/{id}")
    public ApiResponse<Void> deleteCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        webUiAutomationService.deleteCase(id, workspaceCode);
        return ApiResponse.ok(null, "Web UI case deleted");
    }

    @GetMapping("/templates")
    public ApiResponse<PageResponse<WebUiCaseTemplateItem>> listTemplates(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.ok(webUiAutomationService.listTemplates(workspaceCode, keyword, moduleName, status, pageNo, pageSize));
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<WebUiCaseTemplateDetail> getTemplate(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.getTemplate(id, workspaceCode));
    }

    @PostMapping("/templates")
    public ApiResponse<WebUiCaseTemplateDetail> createTemplate(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiCaseTemplateRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.createTemplate(workspaceCode, request), "Web UI template created");
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<WebUiCaseTemplateDetail> updateTemplate(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiCaseTemplateRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.updateTemplate(id, workspaceCode, request), "Web UI template updated");
    }

    @PostMapping("/cases/{id}/save-as-template")
    public ApiResponse<WebUiCaseTemplateDetail> saveCaseAsTemplate(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiTemplateFromCaseRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.saveCaseAsTemplate(id, workspaceCode, request), "Web UI template created from case");
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        webUiAutomationService.deleteTemplate(id, workspaceCode);
        return ApiResponse.ok(null, "Web UI template deleted");
    }

    @PostMapping("/cases/{id}/run")
    public ApiResponse<WebUiRunResponse> runCase(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestBody(required = false) WebUiRunRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.runCase(id, workspaceCode, request), "Web UI case run completed");
    }

    @PostMapping("/batches/run")
    public ApiResponse<WebUiBatchRunResponse> runBatch(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestBody WebUiBatchRunRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.runBatch(workspaceCode, request), "Web UI batch run completed");
    }

    @PostMapping("/ci/batches/run")
    public ApiResponse<WebUiCiBatchRunResponse> runCiBatch(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody WebUiCiBatchRunRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.runCiBatch(authorization, request), "Web UI CI batch run completed");
    }

    @GetMapping("/ci/tokens")
    public ApiResponse<PageResponse<WebUiCiTokenSummary>> listCiTokens(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.ok(webUiAutomationService.listCiTokens(workspaceCode, pageNo, pageSize));
    }

    @PostMapping("/ci/tokens")
    public ApiResponse<WebUiCiTokenCreated> createCiToken(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiCiTokenRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.createCiToken(workspaceCode, request), "Web UI CI token created");
    }

    @PostMapping("/ci/tokens/{id}/disable")
    public ApiResponse<WebUiCiTokenSummary> disableCiToken(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.disableCiToken(id, workspaceCode), "Web UI CI token disabled");
    }

    @PostMapping("/ci/tokens/{id}/rotate")
    public ApiResponse<WebUiCiTokenCreated> rotateCiToken(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.rotateCiToken(id, workspaceCode), "Web UI CI token rotated");
    }

    @DeleteMapping("/ci/tokens/{id}")
    public ApiResponse<Void> deleteCiToken(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        webUiAutomationService.deleteCiToken(id, workspaceCode);
        return ApiResponse.ok(null, "Web UI CI token deleted");
    }

    @PostMapping("/report-shares")
    public ApiResponse<WebUiReportShareCreated> createReportShare(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestBody SaveWebUiReportShareRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.createReportShare(workspaceCode, request), "Web UI report share created");
    }

    @GetMapping("/report-shares")
    public ApiResponse<java.util.List<WebUiReportShareSummary>> listReportShares(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam String shareType,
            @RequestParam Long targetId
    ) {
        return ApiResponse.ok(webUiAutomationService.listReportShares(workspaceCode, shareType, targetId));
    }

    @PostMapping("/report-shares/{id}/revoke")
    public ApiResponse<WebUiReportShareSummary> revokeReportShare(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.revokeReportShare(id, workspaceCode), "Web UI report share revoked");
    }

    @PostMapping("/report-shares/{id}/regenerate")
    public ApiResponse<WebUiReportShareCreated> regenerateReportShare(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.regenerateReportShare(id, workspaceCode), "Web UI report share regenerated");
    }

    @GetMapping("/batches")
    public ApiResponse<PageResponse<WebUiRunBatchSummary>> listBatches(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.ok(webUiAutomationService.listBatches(workspaceCode, keyword, status, pageNo, pageSize));
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<WebUiRunBatchDetail> getBatch(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.getBatch(id, workspaceCode));
    }

    @PostMapping("/cases/debug-run")
    public ApiResponse<WebUiRunResponse> debugRunCase(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody DebugRunWebUiCaseRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.debugRunCase(workspaceCode, request), "Web UI debug run completed");
    }

    @PostMapping("/locators/validate")
    public ApiResponse<ValidateWebUiLocatorResponse> validateLocator(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ValidateWebUiLocatorRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.validateLocator(request), "Web UI locator validated");
    }

    @GetMapping("/runs")
    public ApiResponse<PageResponse<WebUiRunSummary>> listRuns(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(required = false) Long caseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.ok(webUiAutomationService.listRuns(workspaceCode, caseId, keyword, status, pageNo, pageSize));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<WebUiRunDetail> getRun(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.getRun(id, workspaceCode));
    }

    @GetMapping("/runs/{runId}/artifacts/{artifactId}/download")
    public ResponseEntity<Resource> downloadRunArtifact(
            @PathVariable Long runId,
            @PathVariable Long artifactId,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        WebUiArtifactFileDownload download = webUiAutomationService.downloadArtifact(runId, artifactId, workspaceCode);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }

    @GetMapping("/environments")
    public ApiResponse<PageResponse<WebUiEnvironmentItem>> listEnvironments(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(webUiAutomationService.listEnvironments(workspaceCode));
    }

    @PostMapping("/environments")
    public ApiResponse<WebUiEnvironmentItem> createEnvironment(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiEnvironmentRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.createEnvironment(workspaceCode, request), "Web UI environment created");
    }

    @PutMapping("/environments/{id}")
    public ApiResponse<WebUiEnvironmentItem> updateEnvironment(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveWebUiEnvironmentRequest request
    ) {
        return ApiResponse.ok(webUiAutomationService.updateEnvironment(id, workspaceCode, request), "Web UI environment updated");
    }

    @DeleteMapping("/environments/{id}")
    public ApiResponse<Void> deleteEnvironment(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        webUiAutomationService.deleteEnvironment(id, workspaceCode);
        return ApiResponse.ok(null, "Web UI environment deleted");
    }
}
