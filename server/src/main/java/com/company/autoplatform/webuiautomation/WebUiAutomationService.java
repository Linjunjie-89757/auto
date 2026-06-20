package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.PageResponse;
import org.springframework.stereotype.Service;

import java.util.Base64;

import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.*;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.*;

@Service
public class WebUiAutomationService {

    private final WebUiCaseDomainService caseDomainService;
    private final WebUiCaseTemplateDomainService templateDomainService;
    private final WebUiEnvironmentDomainService environmentDomainService;
    private final WebUiExecutionDomainService executionDomainService;
    private final WebUiCiTokenDomainService ciTokenDomainService;
    private final WebUiReportShareDomainService reportShareDomainService;
    private final WebUiLocatorValidationRunner locatorValidationRunner;

    public WebUiAutomationService(
            WebUiCaseDomainService caseDomainService,
            WebUiCaseTemplateDomainService templateDomainService,
            WebUiEnvironmentDomainService environmentDomainService,
            WebUiExecutionDomainService executionDomainService,
            WebUiCiTokenDomainService ciTokenDomainService,
            WebUiReportShareDomainService reportShareDomainService,
            WebUiLocatorValidationRunner locatorValidationRunner
    ) {
        this.caseDomainService = caseDomainService;
        this.templateDomainService = templateDomainService;
        this.environmentDomainService = environmentDomainService;
        this.executionDomainService = executionDomainService;
        this.ciTokenDomainService = ciTokenDomainService;
        this.reportShareDomainService = reportShareDomainService;
        this.locatorValidationRunner = locatorValidationRunner;
    }

    public PageResponse<WebUiCaseItem> listCases(
            String workspaceCode,
            String keyword,
            String moduleName,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        return caseDomainService.listCases(workspaceCode, keyword, moduleName, status, pageNo, pageSize);
    }

    public WebUiCaseDetail getCase(Long id, String workspaceCode) {
        return caseDomainService.getCase(id, workspaceCode);
    }

    public WebUiCaseDetail createCase(String workspaceCode, SaveWebUiCaseRequest request) {
        return caseDomainService.createCase(workspaceCode, request);
    }

    public WebUiCaseDetail updateCase(Long id, String workspaceCode, SaveWebUiCaseRequest request) {
        return caseDomainService.updateCase(id, workspaceCode, request);
    }

    public void deleteCase(Long id, String workspaceCode) {
        caseDomainService.deleteCase(id, workspaceCode);
    }

    public PageResponse<WebUiCaseTemplateItem> listTemplates(
            String workspaceCode,
            String keyword,
            String moduleName,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        return templateDomainService.listTemplates(workspaceCode, keyword, moduleName, status, pageNo, pageSize);
    }

    public WebUiCaseTemplateDetail getTemplate(Long id, String workspaceCode) {
        return templateDomainService.getTemplate(id, workspaceCode);
    }

    public WebUiCaseTemplateDetail createTemplate(String workspaceCode, SaveWebUiCaseTemplateRequest request) {
        return templateDomainService.createTemplate(workspaceCode, request);
    }

    public WebUiCaseTemplateDetail updateTemplate(Long id, String workspaceCode, SaveWebUiCaseTemplateRequest request) {
        return templateDomainService.updateTemplate(id, workspaceCode, request);
    }

    public WebUiCaseTemplateDetail saveCaseAsTemplate(Long caseId, String workspaceCode, SaveWebUiTemplateFromCaseRequest request) {
        return templateDomainService.saveCaseAsTemplate(caseId, workspaceCode, request);
    }

    public void deleteTemplate(Long id, String workspaceCode) {
        templateDomainService.deleteTemplate(id, workspaceCode);
    }

    public WebUiRunResponse runCase(Long id, String workspaceCode, WebUiRunRequest request) {
        return executionDomainService.runCase(id, workspaceCode, request);
    }

    public WebUiBatchRunResponse runBatch(String workspaceCode, WebUiBatchRunRequest request) {
        return executionDomainService.runBatch(workspaceCode, request);
    }

    public WebUiCiBatchRunResponse runCiBatch(String authorization, WebUiCiBatchRunRequest request) {
        return executionDomainService.runCiBatch(authorization, request);
    }

    public PageResponse<WebUiCiTokenSummary> listCiTokens(String workspaceCode, Integer pageNo, Integer pageSize) {
        return ciTokenDomainService.listTokens(workspaceCode, pageNo, pageSize);
    }

    public WebUiCiTokenCreated createCiToken(String workspaceCode, SaveWebUiCiTokenRequest request) {
        return ciTokenDomainService.createToken(workspaceCode, request);
    }

    public WebUiCiTokenSummary disableCiToken(Long id, String workspaceCode) {
        return ciTokenDomainService.disableToken(id, workspaceCode);
    }

    public WebUiCiTokenCreated rotateCiToken(Long id, String workspaceCode) {
        return ciTokenDomainService.rotateToken(id, workspaceCode);
    }

    public void deleteCiToken(Long id, String workspaceCode) {
        ciTokenDomainService.deleteToken(id, workspaceCode);
    }

    public WebUiReportShareCreated createReportShare(String workspaceCode, SaveWebUiReportShareRequest request) {
        return reportShareDomainService.createShare(workspaceCode, request);
    }

    public java.util.List<WebUiReportShareSummary> listReportShares(String workspaceCode, String shareType, Long targetId) {
        return reportShareDomainService.listShares(workspaceCode, shareType, targetId);
    }

    public WebUiReportShareSummary revokeReportShare(Long id, String workspaceCode) {
        return reportShareDomainService.revokeShare(id, workspaceCode);
    }

    public WebUiReportShareCreated regenerateReportShare(Long id, String workspaceCode) {
        return reportShareDomainService.regenerateShare(id, workspaceCode);
    }

    public WebUiSharedReport getSharedReport(String token) {
        return reportShareDomainService.getSharedReport(token);
    }

    public WebUiArtifactFileDownload downloadSharedArtifact(String token, Long runId, Long artifactId) {
        return reportShareDomainService.downloadSharedArtifact(token, runId, artifactId);
    }

    public PageResponse<WebUiRunBatchSummary> listBatches(
            String workspaceCode,
            String keyword,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        return executionDomainService.listBatches(workspaceCode, keyword, status, pageNo, pageSize);
    }

    public WebUiRunBatchDetail getBatch(Long id, String workspaceCode) {
        return executionDomainService.getBatch(id, workspaceCode);
    }

    public WebUiRunResponse debugRunCase(String workspaceCode, DebugRunWebUiCaseRequest request) {
        return executionDomainService.debugRunCase(workspaceCode, request);
    }

    public ValidateWebUiLocatorResponse validateLocator(ValidateWebUiLocatorRequest request) {
        WebUiLocatorValidationRunner.LocatorValidationResult result = locatorValidationRunner.validate(
                new WebUiLocatorValidationRunner.LocatorValidationContext(
                        request.baseUrl().trim(),
                        normalizeBrowserType(request.browserType()),
                        request.headless() == null || request.headless(),
                        request.locatorType(),
                        request.locatorValue(),
                        normalizeStepTimeout(request.timeoutMs())
                )
        );
        return new ValidateWebUiLocatorResponse(
                result.matched(),
                result.matchCount(),
                result.errorMessage(),
                result.screenshotBytes() == null ? null : Base64.getEncoder().encodeToString(result.screenshotBytes())
        );
    }

    public PageResponse<WebUiEnvironmentItem> listEnvironments(String workspaceCode) {
        return environmentDomainService.listEnvironments(workspaceCode);
    }

    public WebUiEnvironmentItem createEnvironment(String workspaceCode, SaveWebUiEnvironmentRequest request) {
        return environmentDomainService.createEnvironment(workspaceCode, request);
    }

    public WebUiEnvironmentItem updateEnvironment(Long id, String workspaceCode, SaveWebUiEnvironmentRequest request) {
        return environmentDomainService.updateEnvironment(id, workspaceCode, request);
    }

    public void deleteEnvironment(Long id, String workspaceCode) {
        environmentDomainService.deleteEnvironment(id, workspaceCode);
    }

    public PageResponse<WebUiRunSummary> listRuns(
            String workspaceCode,
            Long caseId,
            String keyword,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        return executionDomainService.listRuns(workspaceCode, caseId, keyword, status, pageNo, pageSize);
    }

    public WebUiRunDetail getRun(Long id, String workspaceCode) {
        return executionDomainService.getRun(id, workspaceCode);
    }

    public WebUiArtifactFileDownload downloadArtifact(Long runId, Long artifactId, String workspaceCode) {
        return executionDomainService.downloadArtifact(runId, artifactId, workspaceCode);
    }
}
