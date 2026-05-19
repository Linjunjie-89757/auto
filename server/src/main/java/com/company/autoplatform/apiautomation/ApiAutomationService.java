package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.execution.TaskEntity;
import com.company.autoplatform.execution.TaskMapper;
import com.company.autoplatform.settings.EnvConfigEntity;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetEntity;
import com.company.autoplatform.settings.ParamSetMapper;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Service
public class ApiAutomationService {

    private static final String API_ENV_TYPE = "API";
    private static final String API_VARIABLE_SET_TYPE = "API_VARIABLE_SET";
    private static final Set<String> SUCCESS_RESULTS = Set.of("SUCCESS", "FAILED");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ApiDefinitionMapper definitionMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final ApiRunStepResultMapper runStepResultMapper;
    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final TaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final WorkspaceService workspaceService;
    private final ApiAutomationScriptRunner scriptRunner;
    private final HttpClient httpClient;

    public ApiAutomationService(
            ApiDefinitionMapper definitionMapper,
            ApiScenarioMapper scenarioMapper,
            ApiRunStepResultMapper runStepResultMapper,
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper,
            TaskMapper taskMapper,
            ReportMapper reportMapper,
            WorkspaceService workspaceService,
            ApiAutomationScriptRunner scriptRunner
    ) {
        this.definitionMapper = definitionMapper;
        this.scenarioMapper = scenarioMapper;
        this.runStepResultMapper = runStepResultMapper;
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.workspaceService = workspaceService;
        this.scriptRunner = scriptRunner;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public PageResponse<ApiDefinitionItem> listDefinitions(String workspaceCode) {
        LambdaQueryWrapper<ApiDefinitionEntity> query = new LambdaQueryWrapper<>();
        applyWorkspaceScope(query, ApiDefinitionEntity::getWorkspaceId, workspaceCode);
        List<ApiDefinitionItem> items = definitionMapper.selectList(query.orderByDesc(ApiDefinitionEntity::getUpdatedAt))
                .stream()
                .map(this::toDefinitionItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ApiDefinitionDetail getDefinition(Long id, String workspaceCode) {
        ApiDefinitionEntity entity = requireDefinition(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the definition");
        return toDefinitionDetail(entity);
    }

    public ApiDefinitionDetail createDefinition(String headerWorkspaceCode, SaveApiDefinitionRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        ApiDefinitionEntity entity = new ApiDefinitionEntity();
        fillDefinitionEntity(entity, workspace, request);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        definitionMapper.insert(entity);
        return toDefinitionDetail(entity);
    }

    public ApiDefinitionDetail updateDefinition(Long id, String headerWorkspaceCode, SaveApiDefinitionRequest request) {
        ApiDefinitionEntity entity = requireDefinition(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the definition");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Cannot move a definition to another workspace");
        }
        fillDefinitionEntity(entity, workspace, request);
        entity.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(entity);
        return toDefinitionDetail(entity);
    }

    public void deleteDefinition(Long id, String workspaceCode) {
        ApiDefinitionEntity entity = requireDefinition(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the definition");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        long scenarioCount = scenarioMapper.selectCount(new LambdaQueryWrapper<ApiScenarioEntity>()
                .like(ApiScenarioEntity::getStepsJson, "\"definitionId\":" + id));
        if (scenarioCount > 0) {
            throw new BadRequestException("This definition is still referenced by scenarios");
        }
        definitionMapper.deleteById(id);
    }

    public PageResponse<ApiScenarioItem> listScenarios(String workspaceCode) {
        LambdaQueryWrapper<ApiScenarioEntity> query = new LambdaQueryWrapper<>();
        applyWorkspaceScope(query, ApiScenarioEntity::getWorkspaceId, workspaceCode);
        List<ApiScenarioItem> items = scenarioMapper.selectList(query.orderByDesc(ApiScenarioEntity::getUpdatedAt))
                .stream()
                .map(this::toScenarioItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ApiScenarioDetail getScenario(Long id, String workspaceCode) {
        ApiScenarioEntity entity = requireScenario(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the scenario");
        return toScenarioDetail(entity);
    }

    public ApiScenarioDetail createScenario(String headerWorkspaceCode, SaveApiScenarioRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        ApiScenarioEntity entity = new ApiScenarioEntity();
        fillScenarioEntity(entity, workspace, request);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        scenarioMapper.insert(entity);
        return toScenarioDetail(entity);
    }

    public ApiScenarioDetail updateScenario(Long id, String headerWorkspaceCode, SaveApiScenarioRequest request) {
        ApiScenarioEntity entity = requireScenario(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the scenario");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Cannot move a scenario to another workspace");
        }
        fillScenarioEntity(entity, workspace, request);
        entity.setUpdatedAt(LocalDateTime.now());
        scenarioMapper.updateById(entity);
        return toScenarioDetail(entity);
    }

    public void deleteScenario(Long id, String workspaceCode) {
        ApiScenarioEntity entity = requireScenario(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the scenario");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        scenarioMapper.deleteById(id);
    }

    public PageResponse<ApiEnvironmentItem> listEnvironments(String workspaceCode) {
        LambdaQueryWrapper<EnvConfigEntity> query = new LambdaQueryWrapper<>();
        query.eq(EnvConfigEntity::getEnvType, API_ENV_TYPE);
        applyWorkspaceScope(query, EnvConfigEntity::getWorkspaceId, workspaceCode);
        List<ApiEnvironmentItem> items = envConfigMapper.selectList(query.orderByDesc(EnvConfigEntity::getUpdatedAt))
                .stream()
                .map(this::toEnvironmentItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ApiEnvironmentItem createEnvironment(String headerWorkspaceCode, ApiEnvironmentRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        EnvConfigEntity entity = new EnvConfigEntity();
        fillEnvironmentEntity(entity, workspace, request);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.insert(entity);
        return toEnvironmentItem(entity);
    }

    public ApiEnvironmentItem updateEnvironment(Long id, String headerWorkspaceCode, ApiEnvironmentRequest request) {
        EnvConfigEntity entity = requireEnvironment(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the environment");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        fillEnvironmentEntity(entity, workspaceService.requireWorkspaceById(entity.getWorkspaceId()), request);
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.updateById(entity);
        return toEnvironmentItem(entity);
    }

    public void deleteEnvironment(Long id, String workspaceCode) {
        EnvConfigEntity entity = requireEnvironment(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the environment");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        envConfigMapper.deleteById(id);
    }

    public PageResponse<ApiVariableSetItem> listVariableSets(String workspaceCode) {
        LambdaQueryWrapper<ParamSetEntity> query = new LambdaQueryWrapper<>();
        query.eq(ParamSetEntity::getParamType, API_VARIABLE_SET_TYPE);
        applyWorkspaceScope(query, ParamSetEntity::getWorkspaceId, workspaceCode);
        List<ApiVariableSetItem> items = paramSetMapper.selectList(query.orderByDesc(ParamSetEntity::getUpdatedAt))
                .stream()
                .map(this::toVariableSetItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ApiVariableSetItem createVariableSet(String headerWorkspaceCode, ApiVariableSetRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        ParamSetEntity entity = new ParamSetEntity();
        fillVariableSetEntity(entity, workspace, request);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.insert(entity);
        return toVariableSetItem(entity);
    }

    public ApiVariableSetItem updateVariableSet(Long id, String headerWorkspaceCode, ApiVariableSetRequest request) {
        ParamSetEntity entity = requireVariableSet(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the variable set");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        fillVariableSetEntity(entity, workspaceService.requireWorkspaceById(entity.getWorkspaceId()), request);
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.updateById(entity);
        return toVariableSetItem(entity);
    }

    public void deleteVariableSet(Long id, String workspaceCode) {
        ParamSetEntity entity = requireVariableSet(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the variable set");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        paramSetMapper.deleteById(id);
    }

    public ApiRunResponse debugRunDefinition(Long id, String workspaceCode, ApiRunRequest request) {
        ApiDefinitionEntity definition = requireDefinition(id);
        validateReadable(definition.getWorkspaceId(), workspaceCode, "Current workspace cannot run the definition");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(definition.getWorkspaceId()).getWorkspaceCode());

        ExecutionContext context = buildExecutionContext(definition.getWorkspaceId(), request.environmentId(), request.variableSetId());
        RunEnvelope envelope = createRunEnvelope(definition.getWorkspaceId(), "API", "接口调试", definition.getDefinitionName());
        RunStepComputation step = executeDefinition(definition, definition.getDefinitionName(), 1, context.variables(), context.environment());
        persistStep(envelope.report(), definition.getWorkspaceId(), step);
        finalizeRunDefinition(definition, step.success(), envelope.task(), envelope.report(), step);
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                List.of(step.response())
        );
    }

    public ApiRunResponse debugRunDefinitionDraft(String workspaceCode, ApiDebugDefinitionRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWorkspace(
                blankToFallback(request.workspaceCode(), workspaceCode)
        );
        validateReadable(workspace.getId(), workspaceCode, "Current workspace cannot run the definition");
        workspaceService.requireWritableWorkspace(workspace.getWorkspaceCode());

        if (request.definitionId() != null) {
            ApiDefinitionEntity definition = requireDefinition(request.definitionId());
            if (!definition.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Definition does not belong to the selected workspace");
            }
        }

        ApiRequestConfigInput config = request.requestConfig();
        String method = Optional.ofNullable(config.method()).orElse("").trim().toUpperCase();
        String path = Optional.ofNullable(config.path()).orElse("").trim();
        if (method.isEmpty()) {
            throw new BadRequestException("HTTP method cannot be blank");
        }
        if (path.isEmpty()) {
            throw new BadRequestException("Path cannot be blank");
        }

        ApiDefinitionEntity draftDefinition = new ApiDefinitionEntity();
        draftDefinition.setId(request.definitionId());
        draftDefinition.setWorkspaceId(workspace.getId());
        draftDefinition.setDefinitionName(blankToFallback(request.name(), method + " " + path));
        draftDefinition.setHttpMethod(method);
        draftDefinition.setPath(path);
        draftDefinition.setRequestJson(ApiAutomationJsonSupport.toJson(config, "Failed to serialize request config"));
        draftDefinition.setAssertionsJson(ApiAutomationJsonSupport.toJson(defaultList(request.assertions()), "Failed to serialize assertions"));
        draftDefinition.setExtractorsJson(ApiAutomationJsonSupport.toJson(defaultList(request.extractors()), "Failed to serialize extractors"));
        draftDefinition.setPreprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(request.preProcessors(), "PRE"),
                "Failed to serialize pre-processors"));
        draftDefinition.setPostprocessorsJson(ApiAutomationJsonSupport.toJson(normalizePostProcessors(request.postProcessors(), request.extractors()),
                "Failed to serialize post-processors"));

        ExecutionContext context = buildExecutionContext(workspace.getId(), request.environmentId(), request.variableSetId());
        RunEnvelope envelope = createRunEnvelope(workspace.getId(), "API", "接口调试", draftDefinition.getDefinitionName());
        RunStepComputation step = executeDefinition(draftDefinition, draftDefinition.getDefinitionName(), 1, context.variables(), context.environment());
        persistStep(envelope.report(), workspace.getId(), step);
        finalizeRunTaskAndReport(
                envelope.task(),
                envelope.report(),
                step.success() ? "SUCCESS" : "FAILED",
                step.response().errorMessage()
        );
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                List.of(step.response())
        );
    }

    public ApiRunResponse runScenario(Long id, String workspaceCode, ApiRunRequest request) {
        ApiScenarioEntity scenario = requireScenario(id);
        validateReadable(scenario.getWorkspaceId(), workspaceCode, "Current workspace cannot run the scenario");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(scenario.getWorkspaceId()).getWorkspaceCode());

        Long environmentId = request.environmentId() != null ? request.environmentId() : scenario.getDefaultEnvId();
        Long variableSetId = request.variableSetId() != null ? request.variableSetId() : scenario.getVariableSetId();
        ExecutionContext context = buildExecutionContext(scenario.getWorkspaceId(), environmentId, variableSetId);
        RunEnvelope envelope = createRunEnvelope(scenario.getWorkspaceId(), "API", "接口场景", scenario.getScenarioName());
        List<ApiScenarioStepInput> steps = readScenarioSteps(scenario.getStepsJson());
        List<ApiRunStepResultResponse> responses = new ArrayList<>();
        boolean success = true;
        String failureSummary = null;
        int stepOrder = 1;
        for (ApiScenarioStepInput step : steps) {
            if (Boolean.FALSE.equals(step.enabled())) {
                continue;
            }
            ApiDefinitionEntity definition = requireDefinition(step.definitionId());
            validateReadable(definition.getWorkspaceId(), workspaceService.requireWorkspaceById(scenario.getWorkspaceId()).getWorkspaceCode(),
                    "Scenario contains an inaccessible definition");
            RunStepComputation computation = executeDefinition(
                    definition,
                    blankToFallback(step.stepName(), definition.getDefinitionName()),
                    stepOrder,
                    context.variables(),
                    context.environment()
            );
            persistStep(envelope.report(), scenario.getWorkspaceId(), computation);
            responses.add(computation.response());
            if (!computation.success()) {
                success = false;
                failureSummary = blankToFallback(computation.response().errorMessage(), computation.response().stepName() + " failed");
                if (!Boolean.TRUE.equals(scenario.getContinueOnFailure())) {
                    break;
                }
            }
            stepOrder++;
        }

        if (responses.isEmpty()) {
            throw new BadRequestException("Scenario has no enabled steps to run");
        }

        finalizeRunScenario(scenario, success, failureSummary, envelope.task(), envelope.report());
        return new ApiRunResponse(
                envelope.task().getId(),
                envelope.report().getId(),
                envelope.task().getTaskName(),
                envelope.report().getReportName(),
                envelope.report().getResult(),
                envelope.report().getFailureSummary(),
                responses
        );
    }

    public List<ApiRunStepResultResponse> listReportSteps(Long reportId, String workspaceCode) {
        ReportEntity report = requireReport(reportId);
        validateReadable(report.getWorkspaceId(), workspaceCode, "Current workspace cannot access the report");
        return runStepResultMapper.selectList(new LambdaQueryWrapper<ApiRunStepResultEntity>()
                        .eq(ApiRunStepResultEntity::getReportId, reportId)
                        .orderByAsc(ApiRunStepResultEntity::getStepOrder))
                .stream()
                .map(this::toRunStepResponse)
                .toList();
    }

    private void fillDefinitionEntity(ApiDefinitionEntity entity, WorkspaceEntity workspace, SaveApiDefinitionRequest request) {
        entity.setWorkspaceId(workspace.getId());
        entity.setDefinitionName(request.name().trim());
        entity.setHttpMethod(request.requestConfig().method().trim().toUpperCase());
        entity.setPath(request.requestConfig().path().trim());
        entity.setDirectoryName(blankToNull(request.directoryName()));
        entity.setDescription(blankToNull(request.description()));
        entity.setTagsJson(ApiAutomationJsonSupport.toJson(defaultList(request.tags()), "Failed to serialize tags"));
        entity.setRequestJson(ApiAutomationJsonSupport.toJson(request.requestConfig(), "Failed to serialize request config"));
        entity.setAssertionsJson(ApiAutomationJsonSupport.toJson(defaultList(request.assertions()), "Failed to serialize assertions"));
        entity.setExtractorsJson(null);
        entity.setPreprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(request.preProcessors(), "PRE"),
                "Failed to serialize pre-processors"));
        entity.setPostprocessorsJson(ApiAutomationJsonSupport.toJson(normalizePostProcessors(request.postProcessors(), request.extractors()),
                "Failed to serialize post-processors"));
    }

    private void fillScenarioEntity(ApiScenarioEntity entity, WorkspaceEntity workspace, SaveApiScenarioRequest request) {
        List<ApiScenarioStepInput> steps = defaultList(request.steps());
        if (steps.isEmpty()) {
            throw new BadRequestException("Scenario must contain at least one step");
        }
        for (ApiScenarioStepInput step : steps) {
            ApiDefinitionEntity definition = requireDefinition(step.definitionId());
            if (!definition.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Scenario steps must belong to the same workspace");
            }
        }
        if (request.defaultEnvironmentId() != null) {
            EnvConfigEntity environment = requireEnvironment(request.defaultEnvironmentId());
            if (!environment.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Scenario environment must belong to the same workspace");
            }
        }
        if (request.variableSetId() != null) {
            ParamSetEntity variableSet = requireVariableSet(request.variableSetId());
            if (!variableSet.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Scenario variable set must belong to the same workspace");
            }
        }
        entity.setWorkspaceId(workspace.getId());
        entity.setScenarioName(request.name().trim());
        entity.setDirectoryName(blankToNull(request.directoryName()));
        entity.setDescription(blankToNull(request.description()));
        entity.setTagsJson(ApiAutomationJsonSupport.toJson(defaultList(request.tags()), "Failed to serialize tags"));
        entity.setStepsJson(ApiAutomationJsonSupport.toJson(steps, "Failed to serialize scenario steps"));
        entity.setDefaultEnvId(request.defaultEnvironmentId());
        entity.setVariableSetId(request.variableSetId());
        entity.setContinueOnFailure(Boolean.TRUE.equals(request.continueOnFailure()));
        entity.setRelatedCaseId(request.relatedCaseId());
    }

    private void fillEnvironmentEntity(EnvConfigEntity entity, WorkspaceEntity workspace, ApiEnvironmentRequest request) {
        entity.setWorkspaceId(workspace.getId());
        entity.setEnvType(API_ENV_TYPE);
        entity.setEnvName(request.name().trim());
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setConfigJson(ApiAutomationJsonSupport.toJson(new EnvironmentConfigPayload(
                defaultList(request.headers()),
                normalizeAuth(request.authConfig()),
                request.timeoutMs() == null || request.timeoutMs() <= 0 ? 10000 : request.timeoutMs()
        ), "Failed to serialize environment config"));
        entity.setStatus(request.status() == null ? 1 : normalizeStatus(request.status()));
    }

    private void fillVariableSetEntity(ParamSetEntity entity, WorkspaceEntity workspace, ApiVariableSetRequest request) {
        entity.setWorkspaceId(workspace.getId());
        entity.setParamType(API_VARIABLE_SET_TYPE);
        entity.setParamName(request.name().trim());
        entity.setContentJson(ApiAutomationJsonSupport.toJson(defaultList(request.variables()), "Failed to serialize variable set"));
        entity.setStatus(request.status() == null ? 1 : normalizeStatus(request.status()));
    }

    private ApiDefinitionItem toDefinitionItem(ApiDefinitionEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiDefinitionItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getDefinitionName(),
                entity.getHttpMethod(),
                entity.getPath(),
                entity.getDirectoryName(),
                entity.getDescription(),
                readTags(entity.getTagsJson()),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiDefinitionDetail toDefinitionDetail(ApiDefinitionEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiDefinitionDetail(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getDefinitionName(),
                entity.getHttpMethod(),
                entity.getPath(),
                entity.getDirectoryName(),
                entity.getDescription(),
                readTags(entity.getTagsJson()),
                ApiAutomationJsonSupport.read(entity.getRequestJson(), ApiRequestConfigInput.class,
                        new ApiRequestConfigInput(entity.getHttpMethod(), entity.getPath(), 10000, List.of(), List.of(), List.of(),
                                 new ApiRequestBodyInput("NONE", null, List.of(), null, null, null), emptyAuthConfig())),
                readAssertions(entity.getAssertionsJson()),
                List.of(),
                readPreProcessors(entity),
                readPostProcessors(entity),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiScenarioItem toScenarioItem(ApiScenarioEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        List<ApiScenarioStepInput> steps = readScenarioSteps(entity.getStepsJson());
        return new ApiScenarioItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getScenarioName(),
                entity.getDirectoryName(),
                entity.getDescription(),
                readTags(entity.getTagsJson()),
                steps.size(),
                entity.getDefaultEnvId(),
                entity.getVariableSetId(),
                Boolean.TRUE.equals(entity.getContinueOnFailure()),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiScenarioDetail toScenarioDetail(ApiScenarioEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiScenarioDetail(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getScenarioName(),
                entity.getDirectoryName(),
                entity.getDescription(),
                readTags(entity.getTagsJson()),
                entity.getDefaultEnvId(),
                entity.getVariableSetId(),
                Boolean.TRUE.equals(entity.getContinueOnFailure()),
                entity.getRelatedCaseId(),
                readScenarioSteps(entity.getStepsJson()),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiEnvironmentItem toEnvironmentItem(EnvConfigEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        EnvironmentConfigPayload config = ApiAutomationJsonSupport.read(entity.getConfigJson(), EnvironmentConfigPayload.class,
                new EnvironmentConfigPayload(List.of(), emptyAuthConfig(), 10000));
        return new ApiEnvironmentItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getEnvName(),
                entity.getBaseUrl(),
                defaultList(config.headers()),
                normalizeAuth(config.authConfig()),
                config.timeoutMs() == null ? 10000 : config.timeoutMs(),
                entity.getStatus()
        );
    }

    private ApiVariableSetItem toVariableSetItem(ParamSetEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiVariableSetItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getParamName(),
                readVariables(entity.getContentJson()),
                entity.getStatus()
        );
    }

    private ApiRunStepResultResponse toRunStepResponse(ApiRunStepResultEntity entity) {
        return new ApiRunStepResultResponse(
                entity.getId(),
                entity.getReportId(),
                entity.getStepOrder(),
                entity.getStepName(),
                entity.getDefinitionId(),
                Boolean.TRUE.equals(entity.getSuccess()),
                entity.getDurationMs(),
                ApiAutomationJsonSupport.read(entity.getRequestSnapshotJson(), ApiRequestSnapshot.class, null),
                ApiAutomationJsonSupport.read(entity.getResponseSnapshotJson(), ApiResponseSnapshot.class, null),
                readAssertionResults(entity.getAssertionResultsJson()),
                readExtractionResults(entity.getExtractionResultsJson()),
                readProcessorResults(entity.getProcessorResultsJson()),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }

    private ExecutionContext buildExecutionContext(Long workspaceId, Long environmentId, Long variableSetId) {
        ResolvedEnvironment environment = resolveEnvironment(workspaceId, environmentId);
        Map<String, String> variables = new LinkedHashMap<>();
        if (variableSetId != null) {
            ParamSetEntity variableSet = requireVariableSet(variableSetId);
            if (!variableSet.getWorkspaceId().equals(workspaceId)) {
                throw new BadRequestException("Variable set must belong to the same workspace");
            }
            for (ApiVariableItem variable : readVariables(variableSet.getContentJson())) {
                if (variable.name() != null) {
                    variables.put(variable.name(), variable.value() == null ? "" : variable.value());
                }
            }
        }
        return new ExecutionContext(environment, variables);
    }

    private ResolvedEnvironment resolveEnvironment(Long workspaceId, Long environmentId) {
        if (environmentId == null) {
            return new ResolvedEnvironment("", List.of(), emptyAuthConfig(), 10000);
        }
        EnvConfigEntity environment = requireEnvironment(environmentId);
        if (!environment.getWorkspaceId().equals(workspaceId)) {
            throw new BadRequestException("Environment must belong to the same workspace");
        }
        EnvironmentConfigPayload config = ApiAutomationJsonSupport.read(environment.getConfigJson(), EnvironmentConfigPayload.class,
                new EnvironmentConfigPayload(List.of(), emptyAuthConfig(), 10000));
        return new ResolvedEnvironment(
                environment.getBaseUrl(),
                defaultList(config.headers()),
                normalizeAuth(config.authConfig()),
                config.timeoutMs() == null ? 10000 : config.timeoutMs()
        );
    }

    private RunEnvelope createRunEnvelope(Long workspaceId, String engineType, String prefix, String targetName) {
        LocalDateTime now = LocalDateTime.now();
        TaskEntity task = new TaskEntity();
        task.setWorkspaceId(workspaceId);
        task.setTaskName(prefix + " - " + targetName);
        task.setEngineType(engineType);
        task.setTaskStatus("RUNNING");
        task.setSummary(targetName);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        ReportEntity report = new ReportEntity();
        report.setWorkspaceId(workspaceId);
        report.setTaskId(task.getId());
        report.setReportName(targetName + " @ " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        report.setResult("SUCCESS");
        report.setFailureSummary(null);
        report.setLogSource("API");
        report.setLogText(null);
        report.setAttachmentsJson("[]");
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportMapper.insert(report);
        return new RunEnvelope(task, report);
    }

    private RunStepComputation executeDefinition(
            ApiDefinitionEntity definition,
            String stepName,
            int stepOrder,
            Map<String, String> variables,
            ResolvedEnvironment environment
    ) {
        ApiRequestConfigInput config = ApiAutomationJsonSupport.read(definition.getRequestJson(), ApiRequestConfigInput.class,
                new ApiRequestConfigInput(definition.getHttpMethod(), definition.getPath(), 10000, List.of(), List.of(), List.of(),
                        new ApiRequestBodyInput("NONE", null, List.of(), null, null, null), emptyAuthConfig()));
        List<ApiAssertionInput> assertions = readAssertions(definition.getAssertionsJson());
        List<ApiProcessorInput> preProcessors = readPreProcessors(definition);
        List<ApiProcessorInput> postProcessors = readPostProcessors(definition);

        long started = System.currentTimeMillis();
        try {
            MutableRequestConfig requestConfig = toMutableRequestConfig(config);
            List<ApiProcessorResult> processorResults = new ArrayList<>();
            List<ApiExtractionResult> extractionResults = new ArrayList<>();

            executeProcessors("PRE", preProcessors, requestConfig, null, variables, processorResults, extractionResults);

            ApiRequestConfigInput resolvedConfig = toRequestConfig(requestConfig);
            ResolvedRequest request = resolveRequest(resolvedConfig, environment, variables);
            SentRequestResult sentRequest = sendRequest(request, resolvedConfig, environment, variables);
            HttpResponse<String> response = sentRequest.response();
            long durationMs = System.currentTimeMillis() - started;

            ApiResponseSnapshot responseSnapshot = new ApiResponseSnapshot(
                    response.statusCode(),
                    flattenHeaders(response.headers().map()),
                    response.body(),
                    response.headers().firstValue("content-type").orElse(null)
            );
            executeProcessors("POST", postProcessors, requestConfig, responseSnapshot, variables, processorResults, extractionResults);

            List<ApiAssertionResult> assertionResults = evaluateAssertions(assertions, responseSnapshot, durationMs, variables);
            boolean success = assertionResults.stream().allMatch(ApiAssertionResult::success);
            String errorMessage = success ? null : firstFailedMessage(assertionResults);
            ApiRunStepResultResponse result = new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    definition.getId(),
                    success,
                    durationMs,
                    new ApiRequestSnapshot(request.method(), request.url(), sentRequest.headers(), request.body()),
                    responseSnapshot,
                    assertionResults,
                    extractionResults,
                    processorResults,
                    errorMessage,
                    LocalDateTime.now()
            );
            return new RunStepComputation(success, result);
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            long durationMs = System.currentTimeMillis() - started;
            ApiRunStepResultResponse result = new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    definition.getId(),
                    false,
                    durationMs,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    exception.getMessage(),
                    LocalDateTime.now()
            );
            return new RunStepComputation(false, result);
        } catch (RuntimeException exception) {
            long durationMs = System.currentTimeMillis() - started;
            ApiRunStepResultResponse result = new ApiRunStepResultResponse(
                    null,
                    null,
                    stepOrder,
                    stepName,
                    definition.getId(),
                    false,
                    durationMs,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    exception.getMessage(),
                    LocalDateTime.now()
            );
            return new RunStepComputation(false, result);
        }
    }

    private ResolvedRequest resolveRequest(ApiRequestConfigInput config, ResolvedEnvironment environment, Map<String, String> variables) {
        String path = replaceVariables(config.path(), variables);
        String url = path.startsWith("http://") || path.startsWith("https://")
                ? path
                : joinBaseUrl(environment.baseUrl(), path);

        String query = buildQueryString(defaultList(config.queryParams()), variables);
        if (!query.isEmpty()) {
            url = url.contains("?") ? url + "&" + query : url + "?" + query;
        }

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.putAll(toEnabledMap(defaultList(environment.headers()), variables));
        headers.putAll(toEnabledMap(defaultList(config.headers()), variables));
        headers.entrySet().removeIf(entry -> "authorization".equalsIgnoreCase(entry.getKey()));

        LinkedHashMap<String, String> cookies = new LinkedHashMap<>(toEnabledMap(defaultList(config.cookies()), variables));

        String body = null;
        ApiRequestBodyInput bodyConfig = config.body() == null ? new ApiRequestBodyInput("NONE", null, List.of(), null, null, null) : config.body();
        if ("RAW_JSON".equalsIgnoreCase(bodyConfig.type())
                || "RAW_TEXT".equalsIgnoreCase(bodyConfig.type())
                || "RAW_XML".equalsIgnoreCase(bodyConfig.type())) {
            body = replaceVariables(Optional.ofNullable(bodyConfig.rawText()).orElse(""), variables);
        } else if ("FORM_URLENCODED".equalsIgnoreCase(bodyConfig.type())) {
            body = buildQueryString(toEnabledMap(defaultList(bodyConfig.formItems()), variables));
            headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
        } else if ("BINARY".equalsIgnoreCase(bodyConfig.type())) {
            String fileName = Optional.ofNullable(bodyConfig.fileName()).filter(name -> !name.isBlank()).orElse("binary-body");
            String contentType = Optional.ofNullable(bodyConfig.contentType()).filter(value -> !value.isBlank()).orElse("application/octet-stream");
            String base64 = Optional.ofNullable(bodyConfig.binaryBase64()).orElse("");
            body = "[binary] " + fileName + " (" + contentType + ", " + base64.length() + " base64 chars)";
        }

        if (!cookies.isEmpty()) {
            headers.put("Cookie", buildCookieHeader(cookies));
        }
        return new ResolvedRequest(config.method().toUpperCase(), url, headers, body, bodyConfig, normalizeAuth(config.authConfig()));
    }

    private HttpRequest buildHttpRequest(ResolvedRequest request, ApiRequestConfigInput config, ResolvedEnvironment environment) {
        return buildHttpRequest(request, config, environment, null);
    }

    private HttpRequest buildHttpRequest(
            ResolvedRequest request,
            ApiRequestConfigInput config,
            ResolvedEnvironment environment,
            String authorizationHeader
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(Duration.ofMillis(config.timeoutMs() == null || config.timeoutMs() <= 0 ? environment.timeoutMs() : config.timeoutMs()));
        request.headers().forEach(builder::header);
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            builder.header("Authorization", authorizationHeader);
        }

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if ("FORM_DATA".equalsIgnoreCase(request.bodyConfig().type())) {
            MultipartPayload multipart = buildMultipart(defaultList(request.bodyConfig().formItems()));
            builder.header("Content-Type", "multipart/form-data; boundary=" + multipart.boundary());
            publisher = multipart.publisher();
        } else if (request.body() != null) {
            if ("RAW_JSON".equalsIgnoreCase(request.bodyConfig().type())) {
                builder.header("Content-Type", "application/json");
            } else if ("RAW_XML".equalsIgnoreCase(request.bodyConfig().type())) {
                builder.header("Content-Type", Optional.ofNullable(request.bodyConfig().contentType())
                        .filter(value -> !value.isBlank())
                        .orElse("application/xml; charset=UTF-8"));
            } else if ("RAW_TEXT".equalsIgnoreCase(request.bodyConfig().type())) {
                builder.header("Content-Type", "text/plain; charset=UTF-8");
            } else if ("BINARY".equalsIgnoreCase(request.bodyConfig().type())) {
                String base64 = Optional.ofNullable(request.bodyConfig().binaryBase64()).orElse("");
                if (base64.isBlank()) {
                    throw new BadRequestException("Binary body file cannot be empty");
                }
                byte[] content;
                try {
                    content = Base64.getDecoder().decode(base64);
                } catch (IllegalArgumentException exception) {
                    throw new BadRequestException("Binary body content is not valid base64");
                }
                builder.header("Content-Type", Optional.ofNullable(request.bodyConfig().contentType())
                        .filter(value -> !value.isBlank())
                        .orElse("application/octet-stream"));
                publisher = HttpRequest.BodyPublishers.ofByteArray(content);
                return builder.method(request.method(), publisher).build();
            }
            publisher = HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
        }

        return builder.method(request.method(), publisher).build();
    }

    private SentRequestResult sendRequest(
            ResolvedRequest request,
            ApiRequestConfigInput config,
            ResolvedEnvironment environment,
            Map<String, String> variables
    ) throws IOException, InterruptedException {
        ApiAuthConfigInput authConfig = normalizeAuth(request.authConfig());
        String authType = Optional.ofNullable(authConfig.authType()).orElse("NONE").toUpperCase();
        return switch (authType) {
            case "NONE" -> {
                HttpRequest httpRequest = buildHttpRequest(request, config, environment);
                yield new SentRequestResult(
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
                        flattenHeaders(httpRequest.headers().map())
                );
            }
            case "BASIC" -> sendBasicAuthRequest(request, config, environment, variables, authConfig);
            case "DIGEST" -> sendDigestAuthRequest(request, config, environment, variables, authConfig);
            default -> throw new BadRequestException("Unsupported auth type: " + authType);
        };
    }

    private SentRequestResult sendBasicAuthRequest(
            ResolvedRequest request,
            ApiRequestConfigInput config,
            ResolvedEnvironment environment,
            Map<String, String> variables,
            ApiAuthConfigInput authConfig
    ) throws IOException, InterruptedException {
        ApiAuthCredentialInput credential = requireCredential(authConfig.basicAuth(), "Basic");
        String userName = replaceVariables(Optional.ofNullable(credential.userName()).orElse(""), variables);
        String password = replaceVariables(Optional.ofNullable(credential.password()).orElse(""), variables);
        String encoded = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest httpRequest = buildHttpRequest(request, config, environment, "Basic " + encoded);
        return new SentRequestResult(
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
                flattenHeaders(httpRequest.headers().map())
        );
    }

    private SentRequestResult sendDigestAuthRequest(
            ResolvedRequest request,
            ApiRequestConfigInput config,
            ResolvedEnvironment environment,
            Map<String, String> variables,
            ApiAuthConfigInput authConfig
    ) throws IOException, InterruptedException {
        ApiAuthCredentialInput credential = requireCredential(authConfig.digestAuth(), "Digest");
        String userName = replaceVariables(Optional.ofNullable(credential.userName()).orElse(""), variables);
        String password = replaceVariables(Optional.ofNullable(credential.password()).orElse(""), variables);

        HttpRequest initialRequest = buildHttpRequest(request, config, environment);
        HttpResponse<String> initialResponse = httpClient.send(initialRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (initialResponse.statusCode() != 401) {
            return new SentRequestResult(initialResponse, flattenHeaders(initialRequest.headers().map()));
        }

        String challengeHeader = extractDigestChallenge(initialResponse.headers());
        if (challengeHeader == null) {
            return new SentRequestResult(initialResponse, flattenHeaders(initialRequest.headers().map()));
        }

        DigestChallenge challenge = parseDigestChallenge(challengeHeader);
        String authorizationHeader = buildDigestAuthorizationHeader(request, userName, password, challenge);
        HttpRequest digestRequest = buildHttpRequest(request, config, environment, authorizationHeader);
        return new SentRequestResult(
                httpClient.send(digestRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
                flattenHeaders(digestRequest.headers().map())
        );
    }

    private void executeProcessors(
            String stage,
            List<ApiProcessorInput> processors,
            MutableRequestConfig requestConfig,
            ApiResponseSnapshot response,
            Map<String, String> variables,
            List<ApiProcessorResult> processorResults,
            List<ApiExtractionResult> extractionResults
    ) {
        for (ApiProcessorInput processor : processors) {
            if (Boolean.FALSE.equals(processor.enabled())) {
                continue;
            }
            long started = System.currentTimeMillis();
            Map<String, String> beforeVariables = new LinkedHashMap<>(variables);
            try {
                String type = Optional.ofNullable(processor.processorType()).orElse("").trim().toUpperCase();
                String message;
                List<String> logs = List.of();
                switch (type) {
                    case "SCRIPT" -> {
                        ApiAutomationScriptRunner.ScriptExecutionResult scriptResult = executeScriptProcessor(processor, requestConfig, response, variables);
                        variables.clear();
                        variables.putAll(scriptResult.variables());
                        applyScriptRequestChanges(requestConfig, scriptResult.request());
                        message = scriptResult.message();
                        logs = scriptResult.logs();
                    }
                    case "TIME_WAITING" -> {
                        int delayMs = processor.delayMs() == null || processor.delayMs() <= 0 ? 1000 : processor.delayMs();
                        sleep(delayMs);
                        message = "Waited " + delayMs + " ms";
                    }
                    case "EXTRACT" -> {
                        if (!"POST".equals(stage)) {
                            throw new BadRequestException("Extract processor is only supported in post-processors");
                        }
                        message = applyProcessorExtractors(defaultList(processor.extractors()), response, variables, extractionResults);
                    }
                    default -> throw new BadRequestException("Unsupported processor type: " + type);
                }
                processorResults.add(new ApiProcessorResult(
                        stage,
                        type,
                        blankToFallback(processor.name(), defaultProcessorName(type, stage)),
                        true,
                        System.currentTimeMillis() - started,
                        message,
                        logs,
                        diffVariables(beforeVariables, variables)
                ));
            } catch (RuntimeException exception) {
                processorResults.add(new ApiProcessorResult(
                        stage,
                        Optional.ofNullable(processor.processorType()).orElse(""),
                        blankToFallback(processor.name(), defaultProcessorName(processor.processorType(), stage)),
                        false,
                        System.currentTimeMillis() - started,
                        blankToFallback(exception.getMessage(), "Processor failed"),
                        List.of(),
                        diffVariables(beforeVariables, variables)
                ));
                throw new BadRequestException(blankToFallback(processor.name(), defaultProcessorName(processor.processorType(), stage))
                        + " failed: " + blankToFallback(exception.getMessage(), "Processor failed"));
            }
        }
    }

    private ApiAutomationScriptRunner.ScriptExecutionResult executeScriptProcessor(
            ApiProcessorInput processor,
            MutableRequestConfig requestConfig,
            ApiResponseSnapshot response,
            Map<String, String> variables
    ) {
        String script = Optional.ofNullable(processor.script()).orElse("");
        if (script.isBlank()) {
            throw new BadRequestException("Script processor content cannot be blank");
        }
        return scriptRunner.execute(script, new LinkedHashMap<>(variables), requestConfig.toScriptMap(), toResponseContext(response));
    }

    private Map<String, Object> toResponseContext(ApiResponseSnapshot response) {
        if (response == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("statusCode", response.statusCode());
        context.put("headers", response.headers() == null ? Map.of() : response.headers());
        context.put("body", response.body());
        context.put("contentType", response.contentType());
        return context;
    }

    private void applyScriptRequestChanges(MutableRequestConfig requestConfig, Map<String, Object> requestValues) {
        if (requestValues == null || requestValues.isEmpty()) {
            return;
        }
        requestConfig.applyScriptMap(requestValues, OBJECT_MAPPER);
    }

    private void sleep(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Wait processor was interrupted");
        }
    }

    private String applyProcessorExtractors(
            List<ApiProcessorExtractItemInput> extractors,
            ApiResponseSnapshot response,
            Map<String, String> variables,
            List<ApiExtractionResult> extractionResults
    ) {
        if (extractors.isEmpty()) {
            throw new BadRequestException("Extract processor requires at least one extractor");
        }
        int successCount = 0;
        for (ApiProcessorExtractItemInput extractor : extractors) {
            if (Boolean.FALSE.equals(extractor.enabled())) {
                continue;
            }
            try {
                String value = extractValue(response, extractor.sourceType(), extractor.expression());
                variables.put(extractor.name(), value);
                extractionResults.add(new ApiExtractionResult(extractor.name(), true, value, "Extracted"));
                successCount++;
            } catch (Exception exception) {
                extractionResults.add(new ApiExtractionResult(extractor.name(), false, null, exception.getMessage()));
                throw new BadRequestException(exception.getMessage());
            }
        }
        return "Extracted " + successCount + " variable(s)";
    }

    private List<ApiAssertionResult> evaluateAssertions(
            List<ApiAssertionInput> assertions,
            ApiResponseSnapshot response,
            long durationMs,
            Map<String, String> variables
    ) {
        List<ApiAssertionResult> results = new ArrayList<>();
        for (ApiAssertionInput assertion : assertions) {
            String type = Optional.ofNullable(assertion.type()).orElse("").toUpperCase();
            try {
                String subject = replaceVariables(Optional.ofNullable(assertion.subject()).orElse(""), variables);
                String expectedValue = replaceVariables(Optional.ofNullable(assertion.expectedValue()).orElse(""), variables);
                boolean success;
                String actual;
                switch (type) {
                    case "STATUS_CODE" -> {
                        actual = String.valueOf(response.statusCode());
                        success = actual.equals(expectedValue);
                    }
                    case "HEADER_EQUALS" -> {
                        actual = Optional.ofNullable(response.headers().get(subject)).orElse("");
                        success = actual.equals(expectedValue);
                    }
                    case "HEADER_CONTAINS" -> {
                        actual = Optional.ofNullable(response.headers().get(subject)).orElse("");
                        success = actual.contains(expectedValue);
                    }
                    case "BODY_JSONPATH_EQUALS" -> {
                        actual = extractJsonValue(response.body(), subject);
                        success = actual.equals(expectedValue);
                    }
                    case "BODY_JSONPATH_CONTAINS" -> {
                        actual = extractJsonValue(response.body(), subject);
                        success = actual.contains(expectedValue);
                    }
                    case "RESPONSE_TIME_LE" -> {
                        actual = String.valueOf(durationMs);
                        success = durationMs <= Long.parseLong(expectedValue.isBlank() ? "0" : expectedValue);
                    }
                    default -> throw new BadRequestException("Unsupported assertion type: " + type);
                }
                String message = success
                        ? "Assertion passed"
                        : "Expected " + expectedValue + " but got " + actual;
                results.add(new ApiAssertionResult(type, subject, success, message));
            } catch (Exception exception) {
                results.add(new ApiAssertionResult(type, assertion.subject(), false, exception.getMessage()));
            }
        }
        return results;
    }

    private void persistStep(ReportEntity report, Long workspaceId, RunStepComputation computation) {
        ApiRunStepResultResponse response = computation.response();
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
        runStepResultMapper.insert(entity);
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private void finalizeRunDefinition(ApiDefinitionEntity definition, boolean success, TaskEntity task, ReportEntity report, RunStepComputation step) {
        String result = success ? "SUCCESS" : "FAILED";
        definition.setLastRunResult(result);
        definition.setLastRunAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(definition);
        finalizeRunTaskAndReport(task, report, result, step.response().errorMessage());
    }

    private void finalizeRunScenario(ApiScenarioEntity scenario, boolean success, String failureSummary, TaskEntity task, ReportEntity report) {
        String result = success ? "SUCCESS" : "FAILED";
        scenario.setLastRunResult(result);
        scenario.setLastRunAt(LocalDateTime.now());
        scenario.setUpdatedAt(LocalDateTime.now());
        scenarioMapper.updateById(scenario);
        finalizeRunTaskAndReport(task, report, result, failureSummary);
    }

    private void finalizeRunTaskAndReport(TaskEntity task, ReportEntity report, String result, String failureSummary) {
        task.setTaskStatus(result);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        report.setResult(result);
        report.setFailureSummary(blankToNull(failureSummary));
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private String extractJsonValue(String body, String expression) throws IOException {
        if (body == null || body.isBlank()) {
            return "";
        }
        JsonNode current = OBJECT_MAPPER.readTree(body);
        String normalized = Optional.ofNullable(expression).orElse("").trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return current.isValueNode() ? current.asText() : current.toString();
        }
        for (String segment : normalized.split("\\.")) {
            Matcher matcher = Pattern.compile("([\\w-]+)(\\[(\\d+)])?").matcher(segment);
            if (!matcher.matches()) {
                return "";
            }
            current = current.path(matcher.group(1));
            if (matcher.group(3) != null) {
                current = current.path(Integer.parseInt(matcher.group(3)));
            }
        }
        return current.isMissingNode() || current.isNull() ? "" : (current.isValueNode() ? current.asText() : current.toString());
    }

    private String extractValue(ApiResponseSnapshot response, String sourceType, String expression) throws IOException {
        String type = Optional.ofNullable(sourceType).orElse("").trim().toUpperCase();
        return switch (type) {
            case "BODY_JSONPATH" -> extractJsonValue(response.body(), expression);
            case "HEADER" -> Optional.ofNullable(response.headers().get(expression)).orElse("");
            case "STATUS_CODE" -> String.valueOf(response.statusCode());
            default -> throw new BadRequestException("Unsupported extractor type: " + type);
        };
    }

    private Map<String, String> toEnabledMap(List<ApiKeyValueInput> items, Map<String, String> variables) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (ApiKeyValueInput item : items) {
            if (item == null || item.key() == null || item.key().isBlank() || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            result.put(item.key(), replaceVariables(Optional.ofNullable(item.value()).orElse(""), variables));
        }
        return result;
    }

    private String buildQueryString(List<ApiKeyValueInput> items, Map<String, String> variables) {
        List<String> parts = new ArrayList<>();
        for (ApiKeyValueInput item : items) {
            if (item == null || item.key() == null || item.key().isBlank() || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            String key = replaceVariables(item.key(), variables);
            String value = replaceVariables(Optional.ofNullable(item.value()).orElse(""), variables);
            if (Boolean.TRUE.equals(item.encode())) {
                key = URLEncoder.encode(key, StandardCharsets.UTF_8);
                value = URLEncoder.encode(value, StandardCharsets.UTF_8);
            }
            parts.add(key + "=" + value);
        }
        return String.join("&", parts);
    }

    private String replaceVariables(String text, Map<String, String> variables) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!variables.containsKey(key)) {
                throw new BadRequestException("Missing variable: " + key);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Optional.ofNullable(variables.get(key)).orElse("")));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private ApiAuthCredentialInput requireCredential(ApiAuthCredentialInput credential, String authType) {
        if (credential == null
                || credential.userName() == null
                || credential.userName().isBlank()
                || credential.password() == null
                || credential.password().isBlank()) {
            throw new BadRequestException(authType + " auth username and password cannot be blank");
        }
        return credential;
    }

    private String extractDigestChallenge(HttpHeaders headers) {
        for (String value : headers.allValues("WWW-Authenticate")) {
            if (value != null && value.regionMatches(true, 0, "Digest ", 0, 7)) {
                return value;
            }
        }
        return null;
    }

    private DigestChallenge parseDigestChallenge(String header) {
        String content = header.substring(7).trim();
        Matcher matcher = Pattern.compile("(\\w+)=(\"([^\"]*)\"|([^,]+))").matcher(content);
        Map<String, String> values = new LinkedHashMap<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            values.put(key, value == null ? "" : value.trim());
        }
        String realm = values.get("realm");
        String nonce = values.get("nonce");
        if (realm == null || realm.isBlank() || nonce == null || nonce.isBlank()) {
            throw new BadRequestException("Digest auth challenge is missing realm or nonce");
        }
        return new DigestChallenge(
                realm,
                nonce,
                values.get("opaque"),
                Optional.ofNullable(values.get("algorithm")).filter(value -> !value.isBlank()).orElse("MD5"),
                Optional.ofNullable(values.get("qop")).map(String::trim).orElse("")
        );
    }

    private String buildDigestAuthorizationHeader(
            ResolvedRequest request,
            String userName,
            String password,
            DigestChallenge challenge
    ) {
        String algorithm = challenge.algorithm().toUpperCase();
        if (!"MD5".equals(algorithm) && !"MD5-SESS".equals(algorithm)) {
            throw new BadRequestException("Unsupported digest algorithm: " + challenge.algorithm());
        }
        String qop = resolveDigestQop(challenge.qop());
        String uri = URI.create(request.url()).getRawPath();
        String rawQuery = URI.create(request.url()).getRawQuery();
        if (uri == null || uri.isBlank()) {
            uri = "/";
        }
        if (rawQuery != null && !rawQuery.isBlank()) {
            uri = uri + "?" + rawQuery;
        }

        String cnonce = UUID.randomUUID().toString().replace("-", "");
        String nonceCount = "00000001";
        String ha1 = md5Hex(userName + ":" + challenge.realm() + ":" + password);
        if ("MD5-SESS".equals(algorithm)) {
            ha1 = md5Hex(ha1 + ":" + challenge.nonce() + ":" + cnonce);
        }
        String ha2 = md5Hex(request.method() + ":" + uri);
        String response = qop == null
                ? md5Hex(ha1 + ":" + challenge.nonce() + ":" + ha2)
                : md5Hex(ha1 + ":" + challenge.nonce() + ":" + nonceCount + ":" + cnonce + ":" + qop + ":" + ha2);

        List<String> parts = new ArrayList<>();
        parts.add("username=\"" + escapeDigestValue(userName) + "\"");
        parts.add("realm=\"" + escapeDigestValue(challenge.realm()) + "\"");
        parts.add("nonce=\"" + escapeDigestValue(challenge.nonce()) + "\"");
        parts.add("uri=\"" + escapeDigestValue(uri) + "\"");
        parts.add("response=\"" + response + "\"");
        parts.add("algorithm=" + challenge.algorithm());
        if (challenge.opaque() != null && !challenge.opaque().isBlank()) {
            parts.add("opaque=\"" + escapeDigestValue(challenge.opaque()) + "\"");
        }
        if (qop != null) {
            parts.add("qop=" + qop);
            parts.add("nc=" + nonceCount);
            parts.add("cnonce=\"" + cnonce + "\"");
        }
        return "Digest " + String.join(", ", parts);
    }

    private String resolveDigestQop(String qopHeader) {
        if (qopHeader == null || qopHeader.isBlank()) {
            return null;
        }
        for (String candidate : qopHeader.split(",")) {
            String normalized = candidate.trim().toLowerCase();
            if ("auth".equals(normalized)) {
                return "auth";
            }
        }
        throw new BadRequestException("Unsupported digest qop: " + qopHeader);
    }

    private String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.ISO_8859_1));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm is not available", exception);
        }
    }

    private String escapeDigestValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private MultipartPayload buildMultipart(List<ApiKeyValueInput> items) {
        String boundary = "Boundary" + System.nanoTime();
        List<ByteBuffer> buffers = new ArrayList<>();
        for (ApiKeyValueInput item : items) {
            if (item == null || item.key() == null || item.key().isBlank() || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            String part = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + item.key() + "\"\r\n\r\n"
                    + Optional.ofNullable(item.value()).orElse("")
                    + "\r\n";
            buffers.add(StandardCharsets.UTF_8.encode(part));
        }
        buffers.add(StandardCharsets.UTF_8.encode("--" + boundary + "--\r\n"));
        return new MultipartPayload(boundary, HttpRequest.BodyPublishers.fromPublisher(new ByteBufferPublisher(buffers)));
    }

    private String joinBaseUrl(String baseUrl, String path) {
        String normalizedBase = Optional.ofNullable(baseUrl).orElse("").trim();
        if (normalizedBase.isEmpty()) {
            throw new BadRequestException("Environment base URL is required for relative paths");
        }
        if (normalizedBase.endsWith("/") && path.startsWith("/")) {
            return normalizedBase + path.substring(1);
        }
        if (!normalizedBase.endsWith("/") && !path.startsWith("/")) {
            return normalizedBase + "/" + path;
        }
        return normalizedBase + path;
    }

    private String buildQueryString(Map<String, String> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            parts.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(Optional.ofNullable(entry.getValue()).orElse(""), StandardCharsets.UTF_8));
        }
        return String.join("&", parts);
    }

    private String buildCookieHeader(Map<String, String> cookies) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            parts.add(entry.getKey() + "=" + Optional.ofNullable(entry.getValue()).orElse(""));
        }
        return String.join("; ", parts);
    }

    private String firstFailedMessage(List<ApiAssertionResult> results) {
        return results.stream()
                .filter(item -> !item.success())
                .map(ApiAssertionResult::message)
                .findFirst()
                .orElse("Assertion failed");
    }

    private Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        LinkedHashMap<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((key, value) -> flattened.put(key, value == null ? "" : String.join(", ", value)));
        return flattened;
    }

    private List<String> readTags(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiAssertionInput> readAssertions(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiExtractorInput> readExtractors(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiProcessorInput> readPreProcessors(ApiDefinitionEntity entity) {
        return normalizeProcessors(ApiAutomationJsonSupport.readList(entity.getPreprocessorsJson(), new TypeReference<>() {
        }, List.of()), "PRE");
    }

    private List<ApiProcessorInput> readPostProcessors(ApiDefinitionEntity entity) {
        List<ApiProcessorInput> processors = ApiAutomationJsonSupport.readList(entity.getPostprocessorsJson(), new TypeReference<>() {
        }, List.of());
        if (!processors.isEmpty()) {
            return normalizeProcessors(processors, "POST");
        }
        List<ApiExtractorInput> legacyExtractors = readExtractors(entity.getExtractorsJson());
        if (legacyExtractors.isEmpty()) {
            return List.of();
        }
        return List.of(new ApiProcessorInput(
                "legacy-extract",
                "EXTRACT",
                "Extract",
                true,
                null,
                null,
                legacyExtractors.stream()
                        .map(item -> new ApiProcessorExtractItemInput(item.name(), item.sourceType(), item.expression(), true))
                        .toList()
        ));
    }

    private List<ApiScenarioStepInput> readScenarioSteps(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiVariableItem> readVariables(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiAssertionResult> readAssertionResults(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiExtractionResult> readExtractionResults(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiProcessorResult> readProcessorResults(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private List<ApiProcessorInput> normalizePostProcessors(List<ApiProcessorInput> processors, List<ApiExtractorInput> legacyExtractors) {
        List<ApiProcessorInput> normalized = new ArrayList<>(normalizeProcessors(processors, "POST"));
        if (!defaultList(legacyExtractors).isEmpty()) {
            normalized.add(new ApiProcessorInput(
                    "legacy-extract",
                    "EXTRACT",
                    "Extract",
                    true,
                    null,
                    null,
                    defaultList(legacyExtractors).stream()
                            .map(item -> new ApiProcessorExtractItemInput(item.name(), item.sourceType(), item.expression(), true))
                            .toList()
            ));
        }
        return normalized;
    }

    private List<ApiProcessorInput> normalizeProcessors(List<ApiProcessorInput> processors, String stage) {
        List<ApiProcessorInput> normalized = new ArrayList<>();
        int index = 0;
        for (ApiProcessorInput processor : defaultList(processors)) {
            if (processor == null) {
                continue;
            }
            String type = Optional.ofNullable(processor.processorType()).orElse("").trim().toUpperCase();
            if (type.isBlank()) {
                continue;
            }
            if ("PRE".equals(stage) && "EXTRACT".equals(type)) {
                continue;
            }
            normalized.add(new ApiProcessorInput(
                    blankToFallback(processor.id(), stage.toLowerCase() + "-processor-" + index++),
                    type,
                    blankToNull(processor.name()),
                    !Boolean.FALSE.equals(processor.enabled()),
                    Optional.ofNullable(processor.script()).orElse(""),
                    processor.delayMs() == null || processor.delayMs() <= 0 ? 1000 : processor.delayMs(),
                    defaultList(processor.extractors()).stream()
                            .filter(item -> item != null && !blankToFallback(item.name(), "").isBlank())
                            .map(item -> new ApiProcessorExtractItemInput(
                                    item.name().trim(),
                                    blankToFallback(item.sourceType(), "BODY_JSONPATH").toUpperCase(),
                                    Optional.ofNullable(item.expression()).orElse(""),
                                    !Boolean.FALSE.equals(item.enabled())
                            ))
                            .toList()
            ));
        }
        return normalized;
    }

    private ApiAuthConfigInput normalizeAuth(ApiAuthConfigInput authConfig) {
        if (authConfig == null) {
            return emptyAuthConfig();
        }
        return new ApiAuthConfigInput(
                Optional.ofNullable(authConfig.authType()).filter(value -> !value.isBlank()).map(String::toUpperCase).orElse("NONE"),
                normalizeCredential(authConfig.basicAuth()),
                normalizeCredential(authConfig.digestAuth())
        );
    }

    private ApiAuthCredentialInput normalizeCredential(ApiAuthCredentialInput credential) {
        if (credential == null) {
            return new ApiAuthCredentialInput("", "");
        }
        return new ApiAuthCredentialInput(
                Optional.ofNullable(credential.userName()).orElse(""),
                Optional.ofNullable(credential.password()).orElse("")
        );
    }

    private ApiAuthConfigInput emptyAuthConfig() {
        return new ApiAuthConfigInput(
                "NONE",
                new ApiAuthCredentialInput("", ""),
                new ApiAuthCredentialInput("", "")
        );
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BadRequestException("Status must be 0 or 1");
        }
        return status;
    }

    private <T> void applyWorkspaceScope(LambdaQueryWrapper<T> query, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> column, String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        if (workspace != null) {
            query.eq(column, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                query.eq(column, -1L);
            } else {
                query.in(column, workspaceIds);
            }
        }
    }

    private WorkspaceEntity resolveScopedWorkspace(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        return WorkspaceScope.isAll(normalized) ? null : workspaceService.requireReadableWorkspace(normalized);
    }

    private void validateReadable(Long workspaceId, String workspaceCode, String message) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        if (workspace != null && !workspace.getId().equals(workspaceId)) {
            throw new BadRequestException(message);
        }
        if (workspace == null && !workspaceService.isPlatformAdmin()
                && !workspaceService.listReadableWorkspaceIds().contains(workspaceId)) {
            throw new BadRequestException(message);
        }
    }

    private ApiDefinitionEntity requireDefinition(Long id) {
        ApiDefinitionEntity entity = definitionMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API definition not found");
        }
        return entity;
    }

    private ApiScenarioEntity requireScenario(Long id) {
        ApiScenarioEntity entity = scenarioMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API scenario not found");
        }
        return entity;
    }

    private EnvConfigEntity requireEnvironment(Long id) {
        EnvConfigEntity entity = envConfigMapper.selectById(id);
        if (entity == null || !API_ENV_TYPE.equals(entity.getEnvType())) {
            throw new NotFoundException("API environment not found");
        }
        return entity;
    }

    private ParamSetEntity requireVariableSet(Long id) {
        ParamSetEntity entity = paramSetMapper.selectById(id);
        if (entity == null || !API_VARIABLE_SET_TYPE.equals(entity.getParamType())) {
            throw new NotFoundException("API variable set not found");
        }
        return entity;
    }

    private ReportEntity requireReport(Long id) {
        ReportEntity entity = reportMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Report not found");
        }
        return entity;
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String defaultProcessorName(String type, String stage) {
        String normalized = Optional.ofNullable(type).orElse("").trim().toUpperCase();
        return switch (normalized) {
            case "SCRIPT" -> "PRE".equals(stage) ? "Pre Script" : "Post Script";
            case "TIME_WAITING" -> "Wait";
            case "EXTRACT" -> "Extract";
            default -> "Processor";
        };
    }

    private Map<String, String> diffVariables(Map<String, String> before, Map<String, String> after) {
        LinkedHashMap<String, String> changed = new LinkedHashMap<>();
        after.forEach((key, value) -> {
            String beforeValue = before.get(key);
            if (!before.containsKey(key) || !Optional.ofNullable(beforeValue).orElse("").equals(Optional.ofNullable(value).orElse(""))) {
                changed.put(key, value);
            }
        });
        return changed;
    }

    private MutableRequestConfig toMutableRequestConfig(ApiRequestConfigInput config) {
        return new MutableRequestConfig(
                Optional.ofNullable(config.method()).orElse("GET"),
                Optional.ofNullable(config.path()).orElse(""),
                config.timeoutMs(),
                new ArrayList<>(defaultList(config.queryParams())),
                new ArrayList<>(defaultList(config.headers())),
                new ArrayList<>(defaultList(config.cookies())),
                config.body() == null ? new ApiRequestBodyInput("NONE", null, List.of(), null, null, null) : config.body(),
                normalizeAuth(config.authConfig())
        );
    }

    private ApiRequestConfigInput toRequestConfig(MutableRequestConfig config) {
        return new ApiRequestConfigInput(
                config.method(),
                config.path(),
                config.timeoutMs(),
                config.queryParams(),
                config.headers(),
                config.cookies(),
                config.body(),
                config.authConfig()
        );
    }

    private record EnvironmentConfigPayload(
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig,
            Integer timeoutMs
    ) {
    }

    private record ResolvedEnvironment(
            String baseUrl,
            List<ApiKeyValueInput> headers,
            ApiAuthConfigInput authConfig,
            Integer timeoutMs
    ) {
    }

    private record ExecutionContext(
            ResolvedEnvironment environment,
            Map<String, String> variables
    ) {
    }

    private record RunEnvelope(
            TaskEntity task,
            ReportEntity report
    ) {
    }

    private record RunStepComputation(
            boolean success,
            ApiRunStepResultResponse response
    ) {
    }

    private record SentRequestResult(
            HttpResponse<String> response,
            Map<String, String> headers
    ) {
    }

    private record ResolvedRequest(
            String method,
            String url,
            Map<String, String> headers,
            String body,
            ApiRequestBodyInput bodyConfig,
            ApiAuthConfigInput authConfig
    ) {
    }

    private record DigestChallenge(
            String realm,
            String nonce,
            String opaque,
            String algorithm,
            String qop
    ) {
    }

    private class MutableRequestConfig {
        private String method;
        private String path;
        private Integer timeoutMs;
        private List<ApiKeyValueInput> queryParams;
        private List<ApiKeyValueInput> headers;
        private List<ApiKeyValueInput> cookies;
        private ApiRequestBodyInput body;
        private ApiAuthConfigInput authConfig;

        private MutableRequestConfig(
                String method,
                String path,
                Integer timeoutMs,
                List<ApiKeyValueInput> queryParams,
                List<ApiKeyValueInput> headers,
                List<ApiKeyValueInput> cookies,
                ApiRequestBodyInput body,
                ApiAuthConfigInput authConfig
        ) {
            this.method = method;
            this.path = path;
            this.timeoutMs = timeoutMs;
            this.queryParams = queryParams;
            this.headers = headers;
            this.cookies = cookies;
            this.body = body;
            this.authConfig = authConfig;
        }

        private String method() {
            return method;
        }

        private String path() {
            return path;
        }

        private Integer timeoutMs() {
            return timeoutMs;
        }

        private List<ApiKeyValueInput> queryParams() {
            return queryParams;
        }

        private List<ApiKeyValueInput> headers() {
            return headers;
        }

        private List<ApiKeyValueInput> cookies() {
            return cookies;
        }

        private ApiRequestBodyInput body() {
            return body;
        }

        private ApiAuthConfigInput authConfig() {
            return authConfig;
        }

        private Map<String, Object> toScriptMap() {
            LinkedHashMap<String, Object> request = new LinkedHashMap<>();
            request.put("method", method);
            request.put("path", path);
            request.put("timeoutMs", timeoutMs);
            request.put("queryParams", new ArrayList<>(queryParams));
            request.put("headers", new ArrayList<>(headers));
            request.put("cookies", new ArrayList<>(cookies));
            request.put("body", body);
            request.put("authConfig", authConfig);
            return request;
        }

        private void applyScriptMap(Map<String, Object> requestValues, ObjectMapper objectMapper) {
            ApiRequestConfigInput next = objectMapper.convertValue(requestValues, ApiRequestConfigInput.class);
            method = blankToFallback(next.method(), method).toUpperCase();
            path = blankToFallback(next.path(), path);
            timeoutMs = next.timeoutMs() == null ? timeoutMs : next.timeoutMs();
            queryParams = new ArrayList<>(defaultList(next.queryParams()));
            headers = new ArrayList<>(defaultList(next.headers()));
            cookies = new ArrayList<>(defaultList(next.cookies()));
            body = next.body() == null ? body : next.body();
            authConfig = next.authConfig() == null ? authConfig : normalizeAuth(next.authConfig());
        }
    }

    private record MultipartPayload(
            String boundary,
            HttpRequest.BodyPublisher publisher
    ) {
    }

    private static class ByteBufferPublisher implements Flow.Publisher<ByteBuffer> {
        private final List<ByteBuffer> buffers;

        private ByteBufferPublisher(List<ByteBuffer> buffers) {
            this.buffers = buffers;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int index;
                private boolean completed;

                @Override
                public void request(long n) {
                    if (completed) {
                        return;
                    }
                    while (index < buffers.size()) {
                        subscriber.onNext(buffers.get(index++));
                    }
                    completed = true;
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    completed = true;
                }
            });
        }
    }
}
