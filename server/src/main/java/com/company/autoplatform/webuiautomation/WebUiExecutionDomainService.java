package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.apiautomation.ApiWorkspaceScopeSupport;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.settings.EnvConfigEntity;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetEntity;
import com.company.autoplatform.settings.ParamSetMapper;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.blankToNull;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.defaultList;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.normalizeBrowserType;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.normalizeCaseTimeout;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.normalizeScreenshotPolicy;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.normalizeStatus;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.normalizeStepTimeout;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.normalizeStepType;
import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.validateStepRequirements;
import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.*;

@Service
public class WebUiExecutionDomainService {

    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String PASSED = "PASSED";
    private static final String SKIPPED = "SKIPPED";
    private static final String MANUAL = "MANUAL";
    private static final String CI = "CI";
    private static final String BEARER_PREFIX = "Bearer ";

    private final WebUiRunBatchMapper runBatchMapper;
    private final WebUiCiTokenMapper ciTokenMapper;
    private final WebUiRunMapper runMapper;
    private final WebUiRunStepMapper runStepMapper;
    private final WebUiRunArtifactMapper runArtifactMapper;
    private final WebUiCaseMapper caseMapper;
    private final WebUiCaseStepMapper caseStepMapper;
    private final WebUiEnvironmentMapper environmentMapper;
    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;
    private final ObjectProvider<WebUiBrowserRunner> browserRunnerProvider;
    private final WebUiArtifactStorageService artifactStorageService;
    private final WebUiExecutionContextSupport executionContextSupport;

    public WebUiExecutionDomainService(
            WebUiRunBatchMapper runBatchMapper,
            WebUiCiTokenMapper ciTokenMapper,
            WebUiRunMapper runMapper,
            WebUiRunStepMapper runStepMapper,
            WebUiRunArtifactMapper runArtifactMapper,
            WebUiCaseMapper caseMapper,
            WebUiCaseStepMapper caseStepMapper,
            WebUiEnvironmentMapper environmentMapper,
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport,
            ObjectProvider<WebUiBrowserRunner> browserRunnerProvider,
            WebUiArtifactStorageService artifactStorageService,
            WebUiExecutionContextSupport executionContextSupport
    ) {
        this.runBatchMapper = runBatchMapper;
        this.ciTokenMapper = ciTokenMapper;
        this.runMapper = runMapper;
        this.runStepMapper = runStepMapper;
        this.runArtifactMapper = runArtifactMapper;
        this.caseMapper = caseMapper;
        this.caseStepMapper = caseStepMapper;
        this.environmentMapper = environmentMapper;
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
        this.browserRunnerProvider = browserRunnerProvider;
        this.artifactStorageService = artifactStorageService;
        this.executionContextSupport = executionContextSupport;
    }

    @Transactional
    public WebUiRunResponse runCase(Long id, String workspaceCode, WebUiRunRequest request) {
        WebUiCaseEntity webCase = requireCase(id);
        workspaceScopeSupport.validateReadable(webCase.getWorkspaceId(), workspaceCode, "Current workspace cannot run the web UI case");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(webCase.getWorkspaceId()).getWorkspaceCode());
        List<WebUiCaseStepEntity> enabledSteps = listEnabledSteps(webCase.getId());
        if (enabledSteps.isEmpty()) {
            throw new BadRequestException("Web UI case has no enabled steps");
        }
        EnvironmentResolution environment = resolveEnvironment(request == null ? null : request.environmentId(), webCase.getWorkspaceId());
        RunProfile profile = resolveRunProfile(
                webCase,
                environment,
                request == null ? null : request.headless(),
                request == null ? null : request.variableSetId(),
                request == null ? null : request.runtimeVariables()
        );
        return execute(webCase, enabledSteps, profile, true, null, null, CurrentUserContext.require().displayName());
    }

    @Transactional
    public WebUiBatchRunResponse runBatch(String workspaceCode, WebUiBatchRunRequest request) {
        List<Long> caseIds = normalizeBatchCaseIds(request == null ? null : request.caseIds());

        WebUiCaseEntity firstCase = requireCase(caseIds.getFirst());
        workspaceScopeSupport.validateReadable(firstCase.getWorkspaceId(), workspaceCode, "Current workspace cannot run the web UI case");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.requireWorkspaceById(firstCase.getWorkspaceId()).getWorkspaceCode());
        return executeBatch(
                workspace,
                caseIds,
                request == null ? null : request.batchName(),
                request == null ? null : request.environmentId(),
                request == null ? null : request.headless(),
                request != null && Boolean.TRUE.equals(request.stopOnFailure()),
                request == null ? null : request.variableSetId(),
                request == null ? null : request.runtimeVariables(),
                MANUAL,
                null,
                null,
                CurrentUserContext.require().displayName()
        );
    }

    @Transactional
    public WebUiCiBatchRunResponse runCiBatch(String authorization, WebUiCiBatchRunRequest request) {
        WebUiCiTokenEntity token = requireCiToken(authorization);
        if (request == null) {
            throw new BadRequestException("Web UI CI batch run request cannot be empty");
        }
        WorkspaceEntity workspace = workspaceService.requireWorkspace(request.workspaceCode());
        if (!workspace.getId().equals(token.getWorkspaceId())) {
            throw new BadRequestException("Web UI CI token does not belong to the requested workspace");
        }
        WebUiBatchRunResponse response = executeBatch(
                workspace,
                normalizeBatchCaseIds(request.caseIds()),
                request.batchName(),
                request.environmentId(),
                request.headless(),
                Boolean.TRUE.equals(request.stopOnFailure()),
                request.variableSetId(),
                request.runtimeVariables(),
                CI,
                token.getId(),
                request.externalBuildId(),
                "CI:" + token.getTokenName()
        );
        LocalDateTime now = LocalDateTime.now();
        token.setLastUsedAt(now);
        token.setUpdatedAt(now);
        ciTokenMapper.updateById(token);

        String reportUrl = "/automation/web?tab=batches&batchId=" + response.batchId();
        return new WebUiCiBatchRunResponse(
                response.batchId(),
                response.batchName(),
                response.status(),
                SUCCESS.equals(response.status()),
                response.totalCases(),
                response.successCases(),
                response.failedCases(),
                response.durationMs(),
                response.failureSummary(),
                blankToNull(request.externalBuildId()),
                reportUrl,
                buildCiSummaryText(response),
                buildCiFailedRuns(response.runs()),
                response.runs()
        );
    }

    @Transactional
    public WebUiRunResponse debugRunCase(String headerWorkspaceCode, DebugRunWebUiCaseRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        List<WebUiCaseStepEntity> enabledSteps = buildDebugSteps(request.steps()).stream()
                .filter(step -> step.getEnabled() == null || step.getEnabled())
                .toList();
        if (enabledSteps.isEmpty()) {
            throw new BadRequestException("Web UI debug run has no enabled steps");
        }
        EnvironmentResolution environment = resolveEnvironment(request.environmentId(), workspace.getId());
        WebUiCaseEntity debugCase = new WebUiCaseEntity();
        debugCase.setId(request.caseId());
        debugCase.setWorkspaceId(workspace.getId());
        debugCase.setCaseName(request.caseName().trim());
        debugCase.setBaseUrl(blankToNull(request.baseUrl()));
        debugCase.setBrowserType(normalizeBrowserType(request.browserType()));
        debugCase.setHeadless(request.headless() == null || request.headless());
        debugCase.setDefaultTimeoutMs(normalizeCaseTimeout(request.defaultTimeoutMs()));
        debugCase.setStatus(normalizeStatus(request.status()));
        RunProfile profile = resolveRunProfile(debugCase, environment, request.headless(), request.variableSetId(), request.runtimeVariables());
        return execute(debugCase, enabledSteps, profile, false, null, null, CurrentUserContext.require().displayName());
    }

    public PageResponse<WebUiRunBatchSummary> listBatches(
            String workspaceCode,
            String keyword,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        LambdaQueryWrapper<WebUiRunBatchEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, WebUiRunBatchEntity::getWorkspaceId, workspaceCode);
        String trimmedKeyword = blankToNull(keyword);
        if (trimmedKeyword != null) {
            query.like(WebUiRunBatchEntity::getBatchName, trimmedKeyword);
        }
        String trimmedStatus = blankToNull(status);
        if (trimmedStatus != null) {
            query.eq(WebUiRunBatchEntity::getStatus, trimmedStatus.toUpperCase(Locale.ROOT));
        }
        List<WebUiRunBatchSummary> items = runBatchMapper.selectList(query
                        .orderByDesc(WebUiRunBatchEntity::getCreatedAt)
                        .orderByDesc(WebUiRunBatchEntity::getId))
                .stream()
                .map(this::toBatchSummary)
                .toList();
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? (items.isEmpty() ? 10 : items.size()) : pageSize;
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    public WebUiRunBatchDetail getBatch(Long id, String workspaceCode) {
        WebUiRunBatchEntity batch = requireBatch(id);
        workspaceScopeSupport.validateReadable(batch.getWorkspaceId(), workspaceCode, "Current workspace cannot access the web UI batch");
        return buildBatchDetail(batch);
    }

    WebUiRunBatchDetail getSharedBatch(Long id, Long workspaceId) {
        WebUiRunBatchEntity batch = requireBatch(id);
        if (!workspaceId.equals(batch.getWorkspaceId())) {
            throw new NotFoundException("Web UI run batch not found");
        }
        return buildBatchDetail(batch);
    }

    private WebUiRunBatchDetail buildBatchDetail(WebUiRunBatchEntity batch) {
        List<WebUiRunSummary> runs = runMapper.selectList(new LambdaQueryWrapper<WebUiRunEntity>()
                        .eq(WebUiRunEntity::getBatchId, batch.getId())
                        .orderByAsc(WebUiRunEntity::getBatchSortOrder)
                        .orderByAsc(WebUiRunEntity::getId))
                .stream()
                .map(this::toRunSummary)
                .toList();
        return new WebUiRunBatchDetail(toBatchSummary(batch), runs);
    }

    public PageResponse<WebUiRunSummary> listRuns(
            String workspaceCode,
            Long caseId,
            String keyword,
            String status,
            Integer pageNo,
            Integer pageSize
    ) {
        LambdaQueryWrapper<WebUiRunEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, WebUiRunEntity::getWorkspaceId, workspaceCode);
        if (caseId != null) {
            query.eq(WebUiRunEntity::getCaseId, caseId);
        }
        String trimmedKeyword = blankToNull(keyword);
        if (trimmedKeyword != null) {
            query.like(WebUiRunEntity::getCaseName, trimmedKeyword);
        }
        String trimmedStatus = blankToNull(status);
        if (trimmedStatus != null) {
            query.eq(WebUiRunEntity::getStatus, trimmedStatus.toUpperCase(Locale.ROOT));
        }
        List<WebUiRunSummary> items = runMapper.selectList(query
                        .orderByDesc(WebUiRunEntity::getCreatedAt)
                        .orderByDesc(WebUiRunEntity::getId))
                .stream()
                .map(this::toRunSummary)
                .toList();
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? (items.isEmpty() ? 10 : items.size()) : pageSize;
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    public WebUiRunDetail getRun(Long id, String workspaceCode) {
        WebUiRunEntity entity = requireRun(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the web UI run");
        return buildRunDetail(entity, false, null);
    }

    WebUiRunDetail getSharedRun(Long id, Long workspaceId, String token) {
        WebUiRunEntity entity = requireRun(id);
        if (!workspaceId.equals(entity.getWorkspaceId())) {
            throw new NotFoundException("Web UI run not found");
        }
        return buildRunDetail(entity, true, token);
    }

    private WebUiRunDetail buildRunDetail(WebUiRunEntity entity, boolean publicArtifactUrls, String token) {
        List<WebUiRunStepResult> steps = listRunStepResults(entity.getId());
        return new WebUiRunDetail(
                toRunSummary(entity),
                executionContextSupport.readExecutionContextSnapshot(entity.getContextSnapshotJson()),
                publicArtifactUrls ? steps.stream()
                        .map(step -> withPublicArtifactUrl(step, entity.getId(), token))
                        .toList() : steps
        );
    }

    private WebUiRunResponse execute(
            WebUiCaseEntity webCase,
            List<WebUiCaseStepEntity> enabledSteps,
            RunProfile profile,
            boolean updateCaseLastRun,
            Long batchId,
            Integer batchSortOrder,
            String operatorName
    ) {
        WebUiBrowserRunner browserRunner = browserRunnerProvider.getIfAvailable();
        if (browserRunner == null) {
            throw new BadRequestException("Web UI browser runner is not configured");
        }
        List<WebUiCaseStepEntity> runtimeSteps = resolveRuntimeSteps(enabledSteps, profile.variables());
        WebUiRunEntity run = createRun(webCase, profile, runtimeSteps.size(), batchId, batchSortOrder, operatorName);
        List<WebUiRunStepResult> stepResults = new ArrayList<>();
        boolean stopRemaining = false;
        LocalDateTime startedAt = run.getStartedAt();

        List<WebUiBrowserRunner.StepExecutionResult> executionResults = browserRunner.run(new WebUiBrowserRunner.WebUiRunContext(
                profile.browserType(),
                profile.headless(),
                profile.baseUrl(),
                profile.defaultTimeoutMs(),
                runtimeSteps
        ));

        for (WebUiCaseStepEntity step : runtimeSteps) {
            if (stopRemaining) {
                stepResults.add(persistSkippedStep(run.getId(), step));
                continue;
            }
            WebUiBrowserRunner.StepExecutionResult result = findResult(step, executionResults);
            if (result == null) {
                result = new WebUiBrowserRunner.StepExecutionResult(step, false, 0L, "Step was not executed by browser runner", null);
            }
            WebUiRunStepResult persisted = persistExecutedStep(run, result);
            stepResults.add(persisted);
            if (!result.success() && !Boolean.TRUE.equals(step.getContinueOnFailure())) {
                stopRemaining = true;
            }
        }

        int passedSteps = (int) stepResults.stream().filter(step -> PASSED.equals(step.status())).count();
        int failedSteps = (int) stepResults.stream().filter(step -> FAILED.equals(step.status())).count();
        int skippedSteps = (int) stepResults.stream().filter(step -> SKIPPED.equals(step.status())).count();
        String status = failedSteps > 0 ? FAILED : SUCCESS;
        String failureSummary = stepResults.stream()
                .filter(step -> FAILED.equals(step.status()))
                .map(WebUiRunStepResult::errorMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(null);
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
        run.setStatus(status);
        run.setPassedSteps(passedSteps);
        run.setFailedSteps(failedSteps);
        run.setSkippedSteps(skippedSteps);
        run.setDurationMs(durationMs);
        run.setFailureSummary(failureSummary);
        run.setContextSnapshotJson(profile.contextSnapshotJson());
        run.setFinishedAt(finishedAt);
        run.setUpdatedAt(finishedAt);
        runMapper.updateById(run);

        if (updateCaseLastRun) {
            webCase.setLastRunResult(status);
            webCase.setLastRunAt(finishedAt);
            webCase.setUpdatedAt(finishedAt);
            caseMapper.updateById(webCase);
        }

        return new WebUiRunResponse(
                run.getId(),
                run.getBatchId(),
                run.getCaseId(),
                run.getCaseName(),
                status,
                durationMs,
                failureSummary,
                runtimeSteps.size(),
                passedSteps,
                failedSteps,
                skippedSteps,
                stepResults
        );
    }

    private WebUiRunEntity createRun(
            WebUiCaseEntity webCase,
            RunProfile profile,
            int totalSteps,
            Long batchId,
            Integer batchSortOrder,
            String operatorName
    ) {
        LocalDateTime now = LocalDateTime.now();
        WebUiRunEntity run = new WebUiRunEntity();
        run.setWorkspaceId(webCase.getWorkspaceId());
        run.setCaseId(webCase.getId());
        run.setCaseName(webCase.getCaseName());
        run.setBatchId(batchId);
        run.setBatchSortOrder(batchSortOrder);
        run.setEnvironmentId(profile.environmentId());
        run.setEnvironmentName(profile.environmentName());
        run.setStatus(RUNNING);
        run.setBrowserType(profile.browserType());
        run.setHeadless(profile.headless());
        run.setBaseUrl(profile.baseUrl());
        run.setTotalSteps(totalSteps);
        run.setPassedSteps(0);
        run.setFailedSteps(0);
        run.setSkippedSteps(0);
        run.setOperatorName(operatorName);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);
        return run;
    }

    private WebUiRunBatchEntity createBatch(
            WorkspaceEntity workspace,
            String batchName,
            String source,
            EnvironmentResolution environment,
            Long ciTokenId,
            String externalBuildId,
            int totalCases,
            String operatorName
    ) {
        LocalDateTime now = LocalDateTime.now();
        WebUiRunBatchEntity batch = new WebUiRunBatchEntity();
        batch.setWorkspaceId(workspace.getId());
        batch.setBatchName(batchName);
        batch.setSource(source);
        batch.setEnvironmentId(environment == null ? null : environment.bridgeId());
        batch.setEnvironmentName(environment == null ? null : environment.name());
        batch.setStatus(RUNNING);
        batch.setTotalCases(totalCases);
        batch.setSuccessCases(0);
        batch.setFailedCases(0);
        batch.setDurationMs(0L);
        batch.setOperatorName(operatorName);
        batch.setCiTokenId(ciTokenId);
        batch.setExternalBuildId(blankToNull(externalBuildId));
        batch.setStartedAt(now);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        runBatchMapper.insert(batch);
        return batch;
    }

    private WebUiBatchRunResponse executeBatch(
            WorkspaceEntity workspace,
            List<Long> caseIds,
            String batchName,
            Long environmentId,
            Boolean headless,
            boolean stopOnFailure,
            Long variableSetId,
            Map<String, String> runtimeVariables,
            String source,
            Long ciTokenId,
            String externalBuildId,
            String operatorName
    ) {
        EnvironmentResolution environment = resolveEnvironment(environmentId, workspace.getId());
        WebUiRunBatchEntity batch = createBatch(
                workspace,
                blankToNull(batchName) == null ? "Web UI batch run" : batchName.trim(),
                source,
                environment,
                ciTokenId,
                externalBuildId,
                caseIds.size(),
                operatorName
        );

        List<WebUiRunSummary> runSummaries = new ArrayList<>();
        int sortOrder = 1;
        for (Long caseId : caseIds) {
            WebUiCaseEntity webCase = requireCase(caseId);
            if (!workspace.getId().equals(webCase.getWorkspaceId())) {
                throw new BadRequestException("All web UI cases in a batch must belong to the same workspace");
            }
            List<WebUiCaseStepEntity> enabledSteps = listEnabledSteps(webCase.getId());
            if (enabledSteps.isEmpty()) {
                throw new BadRequestException("Web UI case has no enabled steps: " + webCase.getCaseName());
            }
            RunProfile profile = resolveRunProfile(webCase, environment, headless, variableSetId, runtimeVariables);
            WebUiRunResponse runResponse = execute(webCase, enabledSteps, profile, true, batch.getId(), sortOrder, operatorName);
            runSummaries.add(toRunSummary(requireRun(runResponse.runId())));
            sortOrder++;
            if (stopOnFailure && FAILED.equals(runResponse.status())) {
                break;
            }
        }

        WebUiRunBatchEntity finishedBatch = finishBatch(batch, runSummaries);
        return new WebUiBatchRunResponse(
                finishedBatch.getId(),
                finishedBatch.getBatchName(),
                finishedBatch.getStatus(),
                finishedBatch.getTotalCases(),
                finishedBatch.getSuccessCases(),
                finishedBatch.getFailedCases(),
                finishedBatch.getDurationMs(),
                finishedBatch.getFailureSummary(),
                runSummaries
        );
    }

    private List<Long> normalizeBatchCaseIds(List<Long> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            throw new BadRequestException("Web UI batch run requires at least one case");
        }
        List<Long> normalized = caseIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BadRequestException("Web UI batch run requires at least one case");
        }
        return normalized;
    }

    private WebUiCiTokenEntity requireCiToken(String authorization) {
        String rawToken = extractBearerToken(authorization);
        WebUiCiTokenEntity token = ciTokenMapper.selectOne(new LambdaQueryWrapper<WebUiCiTokenEntity>()
                .eq(WebUiCiTokenEntity::getTokenHash, sha256(rawToken))
                .eq(WebUiCiTokenEntity::getStatus, 1));
        if (token == null) {
            throw new BadRequestException("Invalid Web UI CI token");
        }
        return token;
    }

    private String extractBearerToken(String authorization) {
        String trimmed = blankToNull(authorization);
        if (trimmed == null || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BadRequestException("Web UI CI trigger requires Bearer token");
        }
        String token = blankToNull(trimmed.substring(BEARER_PREFIX.length()));
        if (token == null) {
            throw new BadRequestException("Web UI CI trigger requires Bearer token");
        }
        return token;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String buildCiSummaryText(WebUiBatchRunResponse response) {
        if (SUCCESS.equals(response.status())) {
            return "Web UI 批次通过：" + response.successCases() + "/" + response.totalCases() + " 通过";
        }
        String summary = "Web UI 批次失败：" + response.failedCases() + "/" + response.totalCases() + " 失败";
        String firstFailure = blankToNull(response.failureSummary());
        return firstFailure == null ? summary : summary + "，首个失败：" + firstFailure;
    }

    private List<WebUiCiFailedRunSummary> buildCiFailedRuns(List<WebUiRunSummary> runs) {
        return defaultList(runs).stream()
                .filter(run -> FAILED.equals(run.status()))
                .map(run -> new WebUiCiFailedRunSummary(
                        run.id(),
                        run.caseId(),
                        run.caseName(),
                        run.status(),
                        run.failureSummary(),
                        "/automation/web?tab=runs&runId=" + run.id()
                ))
                .toList();
    }

    private WebUiRunBatchEntity finishBatch(WebUiRunBatchEntity batch, List<WebUiRunSummary> runs) {
        LocalDateTime finishedAt = LocalDateTime.now();
        int successCases = (int) runs.stream().filter(run -> SUCCESS.equals(run.status())).count();
        int failedCases = (int) runs.stream().filter(run -> FAILED.equals(run.status())).count();
        String status = failedCases > 0 ? FAILED : SUCCESS;
        String failureSummary = runs.stream()
                .filter(run -> FAILED.equals(run.status()))
                .map(WebUiRunSummary::failureSummary)
                .filter(summary -> summary != null && !summary.isBlank())
                .findFirst()
                .orElse(null);
        batch.setStatus(status);
        batch.setSuccessCases(successCases);
        batch.setFailedCases(failedCases);
        batch.setDurationMs(Math.max(0L, Duration.between(batch.getStartedAt(), finishedAt).toMillis()));
        batch.setFailureSummary(failureSummary);
        batch.setFinishedAt(finishedAt);
        batch.setUpdatedAt(finishedAt);
        runBatchMapper.updateById(batch);
        return batch;
    }

    public WebUiArtifactFileDownload downloadArtifact(Long runId, Long artifactId, String workspaceCode) {
        WebUiRunEntity run = requireRun(runId);
        workspaceScopeSupport.validateReadable(run.getWorkspaceId(), workspaceCode, "Current workspace cannot access the web UI artifact");
        return loadArtifact(run, artifactId);
    }

    WebUiArtifactFileDownload downloadSharedArtifact(Long runId, Long artifactId, Long workspaceId) {
        WebUiRunEntity run = requireRun(runId);
        if (!workspaceId.equals(run.getWorkspaceId())) {
            throw new NotFoundException("Web UI artifact not found");
        }
        return loadArtifact(run, artifactId);
    }

    private WebUiArtifactFileDownload loadArtifact(WebUiRunEntity run, Long artifactId) {
        WebUiRunArtifactEntity artifact = runArtifactMapper.selectById(artifactId);
        if (artifact == null || !run.getId().equals(artifact.getRunId()) || !run.getWorkspaceId().equals(artifact.getWorkspaceId())) {
            throw new NotFoundException("Web UI artifact not found");
        }
        return artifactStorageService.load(artifact);
    }

    private WebUiRunStepResult persistExecutedStep(WebUiRunEntity run, WebUiBrowserRunner.StepExecutionResult result) {
        WebUiCaseStepEntity step = result.step();
        LocalDateTime now = LocalDateTime.now();
        WebUiRunArtifactEntity artifact = persistScreenshotArtifact(run, step, result.screenshotBytes(), now);
        WebUiRunStepEntity entity = new WebUiRunStepEntity();
        entity.setRunId(run.getId());
        entity.setCaseStepId(step.getId());
        entity.setStepName(step.getStepName());
        entity.setStepType(step.getStepType());
        entity.setStatus(result.success() ? PASSED : FAILED);
        entity.setLocatorType(step.getLocatorType());
        entity.setLocatorValue(step.getLocatorValue());
        entity.setInputValueSnapshot(step.getInputValue());
        entity.setDurationMs(result.durationMs());
        entity.setErrorMessage(result.errorMessage());
        entity.setScreenshotArtifactId(artifact == null ? null : artifact.getId());
        entity.setSortOrder(step.getSortOrder());
        entity.setStartedAt(now.minusNanos(Math.max(0L, result.durationMs()) * 1_000_000L));
        entity.setFinishedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        runStepMapper.insert(entity);
        return toRunStepResult(entity);
    }

    private WebUiRunArtifactEntity persistScreenshotArtifact(
            WebUiRunEntity run,
            WebUiCaseStepEntity step,
            byte[] screenshotBytes,
            LocalDateTime now
    ) {
        if (screenshotBytes == null || screenshotBytes.length == 0) {
            return null;
        }
        WebUiArtifactStorageService.StoredArtifact stored = artifactStorageService.storeScreenshot(
                run.getWorkspaceId(),
                run.getId(),
                step.getId(),
                screenshotBytes
        );
        WebUiRunArtifactEntity artifact = new WebUiRunArtifactEntity();
        artifact.setWorkspaceId(run.getWorkspaceId());
        artifact.setRunId(run.getId());
        artifact.setStepId(step.getId());
        artifact.setArtifactType("SCREENSHOT");
        artifact.setFileName(stored.fileName());
        artifact.setContentType(stored.contentType());
        artifact.setFileSize(stored.fileSize());
        artifact.setStoragePath(stored.storagePath());
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        runArtifactMapper.insert(artifact);
        return artifact;
    }

    private WebUiRunStepResult persistSkippedStep(Long runId, WebUiCaseStepEntity step) {
        LocalDateTime now = LocalDateTime.now();
        WebUiRunStepEntity entity = new WebUiRunStepEntity();
        entity.setRunId(runId);
        entity.setCaseStepId(step.getId());
        entity.setStepName(step.getStepName());
        entity.setStepType(step.getStepType());
        entity.setStatus(SKIPPED);
        entity.setLocatorType(step.getLocatorType());
        entity.setLocatorValue(step.getLocatorValue());
        entity.setInputValueSnapshot(step.getInputValue());
        entity.setDurationMs(0L);
        entity.setErrorMessage("Skipped because a previous step failed");
        entity.setSortOrder(step.getSortOrder());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        runStepMapper.insert(entity);
        return toRunStepResult(entity);
    }

    private WebUiBrowserRunner.StepExecutionResult findResult(
            WebUiCaseStepEntity step,
            List<WebUiBrowserRunner.StepExecutionResult> executionResults
    ) {
        return defaultList(executionResults).stream()
                .filter(result -> result != null && sameStep(step, result.step()))
                .findFirst()
                .orElse(null);
    }

    private boolean sameStep(WebUiCaseStepEntity expected, WebUiCaseStepEntity actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.getId() != null && actual.getId() != null) {
            return expected.getId().equals(actual.getId());
        }
        return expected == actual;
    }

    private RunProfile resolveRunProfile(
            WebUiCaseEntity webCase,
            EnvironmentResolution environment,
            Boolean requestHeadless,
            Long requestVariableSetId,
            Map<String, String> runtimeVariables
    ) {
        String browserType = environment == null ? webCase.getBrowserType() : environment.browserType();
        Boolean headless = requestHeadless != null ? requestHeadless : (environment == null ? webCase.getHeadless() : environment.headless());
        String baseUrl = environment == null ? webCase.getBaseUrl() : environment.baseUrl();
        Integer timeoutMs = environment == null ? webCase.getDefaultTimeoutMs() : environment.defaultTimeoutMs();
        VariableSetResolution variableSet = resolveVariableSet(
                requestVariableSetId == null && environment != null ? environment.defaultVariableSetId() : requestVariableSetId,
                webCase.getWorkspaceId()
        );
        Map<String, WebUiExecutionContextSupport.RuntimeVariable> variables = executionContextSupport.mergeVariables(
                executionContextSupport.builtInVariables(),
                environment == null ? List.of() : environment.variables(),
                variableSet == null ? List.of() : variableSet.variables(),
                runtimeVariables
        );
        String normalizedBrowserType = normalizeBrowserType(browserType);
        boolean normalizedHeadless = headless == null || headless;
        String resolvedBaseUrl = executionContextSupport.replaceVariables(blankToNull(baseUrl), variables);
        int normalizedTimeoutMs = normalizeCaseTimeout(timeoutMs);
        return new RunProfile(
                environment == null ? null : environment.bridgeId(),
                environment == null ? null : environment.name(),
                normalizedBrowserType,
                normalizedHeadless,
                resolvedBaseUrl,
                normalizedTimeoutMs,
                variableSet == null ? null : variableSet.id(),
                variableSet == null ? null : variableSet.name(),
                variables,
                executionContextSupport.toJson(new WebUiExecutionContextSupport.ExecutionContextSnapshot(
                        environment == null ? null : new WebUiExecutionContextSupport.EnvironmentSnapshot(
                                environment.bridgeId(),
                                environment.name(),
                                resolvedBaseUrl,
                                normalizedBrowserType,
                                normalizedHeadless,
                                normalizedTimeoutMs
                        ),
                        variableSet == null ? null : variableSet.id(),
                        variableSet == null ? null : variableSet.name(),
                        executionContextSupport.maskVariables(variables)
                ), "Failed to serialize Web UI execution context snapshot")
        );
    }

    private EnvironmentResolution resolveEnvironment(Long environmentId, Long workspaceId) {
        if (environmentId == null) {
            return null;
        }
        if (environmentId < 0) {
            return resolvePublicEnvironment(Math.abs(environmentId), workspaceId);
        }
        WebUiEnvironmentEntity legacyEnvironment = environmentMapper.selectById(environmentId);
        if (legacyEnvironment != null) {
            if (!workspaceId.equals(legacyEnvironment.getWorkspaceId())) {
                throw new NotFoundException("Web UI environment not found");
            }
            if (legacyEnvironment.getStatus() != null && legacyEnvironment.getStatus() == 0) {
                throw new BadRequestException("Web UI environment is disabled");
            }
            return new EnvironmentResolution(
                    legacyEnvironment.getId(),
                    legacyEnvironment.getEnvironmentName(),
                    legacyEnvironment.getBaseUrl(),
                    legacyEnvironment.getBrowserType(),
                    legacyEnvironment.getHeadless(),
                    legacyEnvironment.getDefaultTimeoutMs(),
                    null,
                    List.of()
            );
        }
        return resolvePublicEnvironment(environmentId, workspaceId);
    }

    private EnvironmentResolution resolvePublicEnvironment(Long environmentId, Long workspaceId) {
        EnvConfigEntity environment = envConfigMapper.selectById(environmentId);
        if (environment == null || !WebUiExecutionContextSupport.WEB_UI_ENV_TYPE.equals(environment.getEnvType())
                || !workspaceId.equals(environment.getWorkspaceId())) {
            throw new NotFoundException("Web UI environment not found");
        }
        if (environment.getStatus() != null && environment.getStatus() == 0) {
            throw new BadRequestException("Web UI environment is disabled");
        }
        WebUiExecutionContextSupport.WebUiEnvironmentConfig config =
                executionContextSupport.readEnvironmentConfig(environment.getConfigJson());
        return new EnvironmentResolution(
                -environment.getId(),
                environment.getEnvName(),
                environment.getBaseUrl(),
                config.browserType(),
                config.headless(),
                config.defaultTimeoutMs(),
                config.defaultVariableSetId(),
                config.variables() == null ? List.of() : config.variables()
        );
    }

    private VariableSetResolution resolveVariableSet(Long variableSetId, Long workspaceId) {
        if (variableSetId == null) {
            return null;
        }
        ParamSetEntity variableSet = paramSetMapper.selectById(variableSetId);
        if (variableSet == null || !WebUiExecutionContextSupport.WEB_UI_VARIABLE_SET_TYPE.equals(variableSet.getParamType())) {
            throw new NotFoundException("Web UI variable set not found");
        }
        if (!workspaceId.equals(variableSet.getWorkspaceId())) {
            throw new BadRequestException("Web UI variable set must belong to the same workspace");
        }
        if (variableSet.getStatus() != null && variableSet.getStatus() == 0) {
            throw new BadRequestException("Web UI variable set is disabled");
        }
        return new VariableSetResolution(
                variableSet.getId(),
                variableSet.getParamName(),
                executionContextSupport.readVariables(variableSet.getContentJson())
        );
    }

    private List<WebUiCaseStepEntity> resolveRuntimeSteps(
            List<WebUiCaseStepEntity> steps,
            Map<String, WebUiExecutionContextSupport.RuntimeVariable> variables
    ) {
        return defaultList(steps).stream()
                .map(step -> copyRuntimeStep(step, variables))
                .toList();
    }

    private WebUiCaseStepEntity copyRuntimeStep(
            WebUiCaseStepEntity source,
            Map<String, WebUiExecutionContextSupport.RuntimeVariable> variables
    ) {
        WebUiCaseStepEntity step = new WebUiCaseStepEntity();
        step.setId(source.getId());
        step.setCaseId(source.getCaseId());
        step.setStepName(source.getStepName());
        step.setStepType(source.getStepType());
        step.setLocatorType(source.getLocatorType());
        step.setLocatorValue(executionContextSupport.replaceVariables(source.getLocatorValue(), variables));
        step.setInputValue(executionContextSupport.replaceVariables(source.getInputValue(), variables));
        step.setTimeoutMs(source.getTimeoutMs());
        step.setContinueOnFailure(source.getContinueOnFailure());
        step.setScreenshotPolicy(source.getScreenshotPolicy());
        step.setEnabled(source.getEnabled());
        step.setSortOrder(source.getSortOrder());
        return step;
    }

    private WebUiCaseEntity requireCase(Long id) {
        WebUiCaseEntity entity = caseMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Web UI case not found");
        }
        return entity;
    }

    private WebUiRunEntity requireRun(Long id) {
        WebUiRunEntity entity = runMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Web UI run not found");
        }
        return entity;
    }

    private WebUiRunBatchEntity requireBatch(Long id) {
        WebUiRunBatchEntity entity = runBatchMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Web UI run batch not found");
        }
        return entity;
    }

    private List<WebUiCaseStepEntity> listEnabledSteps(Long caseId) {
        return caseStepMapper.selectList(new LambdaQueryWrapper<WebUiCaseStepEntity>()
                        .eq(WebUiCaseStepEntity::getCaseId, caseId)
                        .eq(WebUiCaseStepEntity::getEnabled, true)
                        .orderByAsc(WebUiCaseStepEntity::getSortOrder)
                        .orderByAsc(WebUiCaseStepEntity::getId));
    }

    private List<WebUiCaseStepEntity> buildDebugSteps(List<SaveWebUiCaseStepRequest> requests) {
        List<SaveWebUiCaseStepRequest> sortedRequests = defaultList(requests).stream()
                .filter(request -> request != null)
                .sorted(Comparator.comparingInt(request -> request.sortOrder() == null ? Integer.MAX_VALUE : request.sortOrder()))
                .toList();
        List<WebUiCaseStepEntity> steps = new ArrayList<>();
        int fallbackSortOrder = 1;
        for (SaveWebUiCaseStepRequest request : sortedRequests) {
            WebUiCaseStepEntity step = new WebUiCaseStepEntity();
            String stepType = normalizeStepType(request.stepType());
            String locatorType = blankToNull(request.locatorType());
            String locatorValue = blankToNull(request.locatorValue());
            String inputValue = blankToNull(request.inputValue());
            validateStepRequirements(stepType, locatorType, locatorValue, inputValue);
            step.setStepName(request.stepName().trim());
            step.setStepType(stepType);
            step.setLocatorType(locatorType);
            step.setLocatorValue(locatorValue);
            step.setInputValue(inputValue);
            step.setTimeoutMs(normalizeStepTimeout(request.timeoutMs()));
            step.setContinueOnFailure(Boolean.TRUE.equals(request.continueOnFailure()));
            step.setScreenshotPolicy(normalizeScreenshotPolicy(request.screenshotPolicy()));
            step.setEnabled(request.enabled() == null || request.enabled());
            step.setSortOrder(request.sortOrder() == null ? fallbackSortOrder : request.sortOrder());
            steps.add(step);
            fallbackSortOrder++;
        }
        return steps;
    }

    private List<WebUiRunStepResult> listRunStepResults(Long runId) {
        return runStepMapper.selectList(new LambdaQueryWrapper<WebUiRunStepEntity>()
                        .eq(WebUiRunStepEntity::getRunId, runId)
                        .orderByAsc(WebUiRunStepEntity::getSortOrder)
                        .orderByAsc(WebUiRunStepEntity::getId))
                .stream()
                .map(this::toRunStepResult)
                .toList();
    }

    private WebUiRunSummary toRunSummary(WebUiRunEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new WebUiRunSummary(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getBatchId(),
                entity.getBatchSortOrder(),
                entity.getCaseId(),
                entity.getCaseName(),
                entity.getEnvironmentId(),
                entity.getEnvironmentName(),
                entity.getStatus(),
                entity.getBrowserType(),
                entity.getHeadless(),
                entity.getBaseUrl(),
                entity.getDurationMs(),
                entity.getFailureSummary(),
                entity.getTotalSteps(),
                entity.getPassedSteps(),
                entity.getFailedSteps(),
                entity.getSkippedSteps(),
                entity.getOperatorName(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt()
        );
    }

    private WebUiRunBatchSummary toBatchSummary(WebUiRunBatchEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new WebUiRunBatchSummary(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getBatchName(),
                entity.getSource(),
                entity.getEnvironmentId(),
                entity.getEnvironmentName(),
                entity.getStatus(),
                entity.getTotalCases(),
                entity.getSuccessCases(),
                entity.getFailedCases(),
                entity.getDurationMs(),
                entity.getFailureSummary(),
                entity.getOperatorName(),
                entity.getCiTokenId(),
                entity.getExternalBuildId(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt()
        );
    }

    private WebUiRunStepResult toRunStepResult(WebUiRunStepEntity entity) {
        String screenshotUrl = entity.getScreenshotArtifactId() == null
                ? null
                : "/api/automation/web/runs/" + entity.getRunId()
                + "/artifacts/" + entity.getScreenshotArtifactId() + "/download";
        return new WebUiRunStepResult(
                entity.getId(),
                entity.getCaseStepId(),
                entity.getStepName(),
                entity.getStepType(),
                entity.getStatus(),
                entity.getLocatorType(),
                entity.getLocatorValue(),
                entity.getInputValueSnapshot(),
                entity.getDurationMs(),
                entity.getErrorMessage(),
                entity.getScreenshotArtifactId(),
                screenshotUrl,
                entity.getSortOrder(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private WebUiRunStepResult withPublicArtifactUrl(WebUiRunStepResult step, Long runId, String token) {
        if (step.screenshotArtifactId() == null) {
            return step;
        }
        return new WebUiRunStepResult(
                step.id(),
                step.caseStepId(),
                step.stepName(),
                step.stepType(),
                step.status(),
                step.locatorType(),
                step.locatorValue(),
                step.inputValueSnapshot(),
                step.durationMs(),
                step.errorMessage(),
                step.screenshotArtifactId(),
                "/api/public/automation/web/report-shares/" + token
                        + "/runs/" + runId
                        + "/artifacts/" + step.screenshotArtifactId() + "/download",
                step.sortOrder(),
                step.startedAt(),
                step.finishedAt()
        );
    }

    private record RunProfile(
            Long environmentId,
            String environmentName,
            String browserType,
            boolean headless,
            String baseUrl,
            int defaultTimeoutMs,
            Long variableSetId,
            String variableSetName,
            Map<String, WebUiExecutionContextSupport.RuntimeVariable> variables,
            String contextSnapshotJson
    ) {
    }

    private record EnvironmentResolution(
            Long bridgeId,
            String name,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            Long defaultVariableSetId,
            List<WebUiExecutionContextSupport.VariableItem> variables
    ) {
    }

    private record VariableSetResolution(
            Long id,
            String name,
            List<WebUiExecutionContextSupport.VariableItem> variables
    ) {
    }
}
