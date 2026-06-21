package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.blankToNull;
import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Service
public class ApiExecutionSuiteDomainService {

    private final ApiExecutionSuiteModuleMapper suiteModuleMapper;
    private final ApiExecutionSuiteMapper suiteMapper;
    private final ApiExecutionSuiteItemMapper suiteItemMapper;
    private final ApiExecutionSuiteRunHistoryMapper suiteRunHistoryMapper;
    private final ApiDefinitionCaseMapper caseMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final ObjectProvider<ApiExecutionDomainService> executionDomainServiceProvider;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;

    public ApiExecutionSuiteDomainService(
            ApiExecutionSuiteModuleMapper suiteModuleMapper,
            ApiExecutionSuiteMapper suiteMapper,
            ApiExecutionSuiteItemMapper suiteItemMapper,
            ApiExecutionSuiteRunHistoryMapper suiteRunHistoryMapper,
            ApiDefinitionCaseMapper caseMapper,
            ApiScenarioMapper scenarioMapper,
            ObjectProvider<ApiExecutionDomainService> executionDomainServiceProvider,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport
    ) {
        this.suiteModuleMapper = suiteModuleMapper;
        this.suiteMapper = suiteMapper;
        this.suiteItemMapper = suiteItemMapper;
        this.suiteRunHistoryMapper = suiteRunHistoryMapper;
        this.caseMapper = caseMapper;
        this.scenarioMapper = scenarioMapper;
        this.executionDomainServiceProvider = executionDomainServiceProvider;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
    }

    public List<ApiExecutionSuiteModuleItem> listSuiteModules(String workspaceCode) {
        WorkspaceEntity scopedWorkspace = workspaceScopeSupport.resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<ApiExecutionSuiteModuleEntity> query = new LambdaQueryWrapper<>();
        if (scopedWorkspace != null) {
            query.eq(ApiExecutionSuiteModuleEntity::getWorkspaceId, scopedWorkspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            query.in(ApiExecutionSuiteModuleEntity::getWorkspaceId, workspaceIds.isEmpty() ? List.of(-1L) : workspaceIds);
        }
        List<ApiExecutionSuiteModuleEntity> modules = suiteModuleMapper.selectList(query
                .orderByAsc(ApiExecutionSuiteModuleEntity::getSortOrder)
                .orderByAsc(ApiExecutionSuiteModuleEntity::getId));
        Map<Long, Long> counts = suiteMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteEntity>())
                .stream()
                .filter(suite -> modules.stream().anyMatch(module -> module.getWorkspaceId().equals(suite.getWorkspaceId())))
                .filter(suite -> suite.getModuleId() != null)
                .collect(java.util.stream.Collectors.groupingBy(ApiExecutionSuiteEntity::getModuleId, java.util.stream.Collectors.counting()));
        return buildSuiteModuleTree(modules, counts, null);
    }

    public ApiExecutionSuiteModuleItem createSuiteModule(String headerWorkspaceCode, ApiExecutionSuiteModuleRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (request.parentId() != null) {
            ApiExecutionSuiteModuleEntity parent = requireSuiteModule(request.parentId());
            if (!parent.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Parent module must belong to the same workspace");
            }
        }
        ensureSuiteModuleNameUnique(workspace.getId(), request.parentId(), null, request.name());
        ApiExecutionSuiteModuleEntity entity = new ApiExecutionSuiteModuleEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setParentId(request.parentId());
        entity.setModuleName(request.name().trim());
        entity.setSortOrder(nextSuiteModuleSort(workspace.getId(), request.parentId()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        suiteModuleMapper.insert(entity);
        return toSuiteModuleItem(entity, 0L, List.of());
    }

    public ApiExecutionSuiteModuleItem updateSuiteModule(Long id, String workspaceCode, ApiExecutionSuiteModuleRequest request) {
        ApiExecutionSuiteModuleEntity entity = requireSuiteModule(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot edit the execution suite module");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        ensureSuiteModuleNameUnique(entity.getWorkspaceId(), entity.getParentId(), id, request.name());
        entity.setModuleName(request.name().trim());
        entity.setUpdatedAt(LocalDateTime.now());
        suiteModuleMapper.updateById(entity);
        return toSuiteModuleItem(entity, countSuitesInModule(id), List.of());
    }

    public ApiExecutionSuiteModuleItem moveSuiteModule(Long id, String workspaceCode, MoveApiExecutionSuiteModuleRequest request) {
        ApiExecutionSuiteModuleEntity entity = requireSuiteModule(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot move the execution suite module");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        if (request.parentId() != null) {
            ApiExecutionSuiteModuleEntity parent = requireSuiteModule(request.parentId());
            if (!parent.getWorkspaceId().equals(entity.getWorkspaceId())) {
                throw new BadRequestException("Parent module must belong to the same workspace");
            }
            if (suiteModuleDescendantIds(entity.getWorkspaceId(), id).contains(request.parentId())) {
                throw new BadRequestException("Cannot move module under itself");
            }
        }
        ensureSuiteModuleNameUnique(entity.getWorkspaceId(), request.parentId(), id, entity.getModuleName());
        entity.setParentId(request.parentId());
        entity.setSortOrder(request.sortOrder() == null ? nextSuiteModuleSort(entity.getWorkspaceId(), request.parentId()) : request.sortOrder());
        entity.setUpdatedAt(LocalDateTime.now());
        suiteModuleMapper.updateById(entity);
        return toSuiteModuleItem(entity, countSuitesInModule(id), List.of());
    }

    public void deleteSuiteModule(Long id, String workspaceCode) {
        ApiExecutionSuiteModuleEntity entity = requireSuiteModule(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the execution suite module");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        if (suiteModuleMapper.selectCount(new LambdaQueryWrapper<ApiExecutionSuiteModuleEntity>()
                .eq(ApiExecutionSuiteModuleEntity::getParentId, id)) > 0) {
            throw new BadRequestException("Cannot delete a module that contains child modules");
        }
        if (suiteMapper.selectCount(new LambdaQueryWrapper<ApiExecutionSuiteEntity>()
                .eq(ApiExecutionSuiteEntity::getModuleId, id)) > 0) {
            throw new BadRequestException("Cannot delete a module that contains execution suites");
        }
        suiteModuleMapper.deleteById(id);
    }

    public PageResponse<ApiExecutionSuiteItem> listSuites(
            String workspaceCode,
            Long moduleId,
            String keyword,
            Integer pageNo,
            Integer pageSize
    ) {
        LambdaQueryWrapper<ApiExecutionSuiteEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, ApiExecutionSuiteEntity::getWorkspaceId, workspaceCode);
        if (moduleId != null) {
            ApiExecutionSuiteModuleEntity module = requireSuiteModule(moduleId);
            workspaceScopeSupport.validateReadable(module.getWorkspaceId(), workspaceCode, "Current workspace cannot access the execution suite module");
            query.in(ApiExecutionSuiteEntity::getModuleId, suiteModuleDescendantIds(module.getWorkspaceId(), moduleId));
        }
        String trimmedKeyword = blankToNull(keyword);
        if (trimmedKeyword != null) {
            query.and(wrapper -> wrapper.like(ApiExecutionSuiteEntity::getSuiteName, trimmedKeyword)
                    .or()
                    .like(ApiExecutionSuiteEntity::getDescription, trimmedKeyword));
        }
        List<ApiExecutionSuiteItem> items = suiteMapper.selectList(query.orderByDesc(ApiExecutionSuiteEntity::getUpdatedAt))
                .stream()
                .map(this::toSuiteItem)
                .toList();
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? (items.isEmpty() ? 10 : items.size()) : pageSize;
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    public ApiExecutionSuiteDetail getSuite(Long id, String workspaceCode) {
        ApiExecutionSuiteEntity entity = requireSuite(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the execution suite");
        return toSuiteDetail(entity);
    }

    public ApiExecutionSuiteDetail createSuite(String headerWorkspaceCode, SaveApiExecutionSuiteRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        ApiExecutionSuiteEntity entity = new ApiExecutionSuiteEntity();
        fillSuiteEntity(entity, workspace, request);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        suiteMapper.insert(entity);
        return toSuiteDetail(entity);
    }

    public ApiExecutionSuiteDetail updateSuite(Long id, String headerWorkspaceCode, SaveApiExecutionSuiteRequest request) {
        ApiExecutionSuiteEntity entity = requireSuite(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the execution suite");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Cannot move an execution suite to another workspace");
        }
        fillSuiteEntity(entity, workspace, request);
        entity.setUpdatedAt(LocalDateTime.now());
        suiteMapper.updateById(entity);
        return toSuiteDetail(entity);
    }

    public void deleteSuite(Long id, String workspaceCode) {
        ApiExecutionSuiteEntity entity = requireSuite(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the execution suite");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        suiteItemMapper.delete(new LambdaQueryWrapper<ApiExecutionSuiteItemEntity>()
                .eq(ApiExecutionSuiteItemEntity::getSuiteId, id));
        suiteMapper.deleteById(id);
    }

    public List<ApiExecutionSuiteItemDetail> listSuiteItems(Long suiteId, String workspaceCode) {
        ApiExecutionSuiteEntity suite = requireSuite(suiteId);
        workspaceScopeSupport.validateReadable(suite.getWorkspaceId(), workspaceCode, "Current workspace cannot access the execution suite");
        return suiteItemMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteItemEntity>()
                        .eq(ApiExecutionSuiteItemEntity::getSuiteId, suiteId)
                        .orderByAsc(ApiExecutionSuiteItemEntity::getSortOrder)
                        .orderByAsc(ApiExecutionSuiteItemEntity::getId))
                .stream()
                .map(this::toSuiteItemDetail)
                .toList();
    }

    public ApiExecutionSuiteItemDetail addSuiteItem(Long suiteId, String workspaceCode, ApiExecutionSuiteItemRequest request) {
        ApiExecutionSuiteEntity suite = requireSuite(suiteId);
        workspaceScopeSupport.validateReadable(suite.getWorkspaceId(), workspaceCode, "Current workspace cannot edit the execution suite");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(suite.getWorkspaceId()).getWorkspaceCode());
        ResolvedSuiteItem resolved = resolveSuiteItem(suite.getWorkspaceId(), request.itemType(), request.itemId());
        ApiExecutionSuiteItemEntity entity = new ApiExecutionSuiteItemEntity();
        entity.setWorkspaceId(suite.getWorkspaceId());
        entity.setSuiteId(suiteId);
        entity.setItemType(resolved.itemType());
        entity.setItemId(request.itemId());
        entity.setItemNameSnapshot(resolved.itemName());
        entity.setSortOrder(nextSuiteItemSort(suiteId));
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setDescription(blankToNull(request.description()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        suiteItemMapper.insert(entity);
        return toSuiteItemDetail(entity);
    }

    public List<ApiExecutionSuiteItemDetail> reorderSuiteItems(Long suiteId, String workspaceCode, ApiExecutionSuiteItemOrderRequest request) {
        ApiExecutionSuiteEntity suite = requireSuite(suiteId);
        workspaceScopeSupport.validateReadable(suite.getWorkspaceId(), workspaceCode, "Current workspace cannot edit the execution suite");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(suite.getWorkspaceId()).getWorkspaceCode());
        for (ApiExecutionSuiteItemOrderInput item : request.items() == null ? List.<ApiExecutionSuiteItemOrderInput>of() : request.items()) {
            ApiExecutionSuiteItemEntity entity = requireSuiteItem(suiteId, item.id());
            entity.setSortOrder(item.sortOrder() == null ? entity.getSortOrder() : item.sortOrder());
            if (item.enabled() != null) {
                entity.setEnabled(item.enabled());
            }
            entity.setUpdatedAt(LocalDateTime.now());
            suiteItemMapper.updateById(entity);
        }
        return listSuiteItems(suiteId, workspaceCode);
    }

    public void deleteSuiteItem(Long suiteId, Long itemId, String workspaceCode) {
        ApiExecutionSuiteEntity suite = requireSuite(suiteId);
        workspaceScopeSupport.validateReadable(suite.getWorkspaceId(), workspaceCode, "Current workspace cannot edit the execution suite");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(suite.getWorkspaceId()).getWorkspaceCode());
        requireSuiteItem(suiteId, itemId);
        suiteItemMapper.deleteById(itemId);
    }

    public ApiRunResponse runSuite(Long suiteId, String workspaceCode, ApiRunRequest request) {
        if (WorkspaceScope.isAll(WorkspaceScope.normalize(workspaceCode))) {
            throw new BadRequestException("Please switch to a concrete workspace before running execution suite");
        }
        ApiExecutionSuiteEntity suite = requireSuite(suiteId);
        workspaceScopeSupport.validateReadable(suite.getWorkspaceId(), workspaceCode, "Current workspace cannot run the execution suite");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(suite.getWorkspaceId()).getWorkspaceCode());
        List<ApiExecutionSuiteItemEntity> enabledItems = suiteItemMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteItemEntity>()
                        .eq(ApiExecutionSuiteItemEntity::getSuiteId, suiteId)
                        .eq(ApiExecutionSuiteItemEntity::getEnabled, true)
                        .orderByAsc(ApiExecutionSuiteItemEntity::getSortOrder)
                        .orderByAsc(ApiExecutionSuiteItemEntity::getId));
        if (enabledItems.isEmpty()) {
            throw new BadRequestException("Execution suite has no enabled items to run");
        }

        ApiRunRequest effectiveRequest = new ApiRunRequest(
                workspace.getWorkspaceCode(),
                request == null || request.environmentId() == null ? suite.getEnvironmentId() : request.environmentId(),
                request == null || request.variableSetId() == null ? suite.getVariableSetId() : request.variableSetId()
        );
        ApiExecutionDomainService executionDomainService = executionDomainServiceProvider.getObject();
        List<ApiRunStepResultResponse> stepResults = new ArrayList<>();
        Long taskId = null;
        Long reportId = null;
        String failureSummary = null;
        boolean success = true;
        for (ApiExecutionSuiteItemEntity item : enabledItems) {
            ApiRunResponse itemRun = switch (item.getItemType()) {
                case "API_CASE" -> executionDomainService.runCase(item.getItemId(), workspace.getWorkspaceCode(), effectiveRequest);
                case "SCENARIO" -> executionDomainService.runScenario(item.getItemId(), workspace.getWorkspaceCode(), effectiveRequest);
                default -> throw new BadRequestException("Unsupported execution suite item type");
            };
            if (taskId == null) {
                taskId = itemRun.taskId();
                reportId = itemRun.reportId();
            }
            stepResults.addAll(itemRun.stepResults());
            if (!"SUCCESS".equals(itemRun.result())) {
                success = false;
                if (failureSummary == null) {
                    failureSummary = itemRun.failureSummary();
                }
                if (!Boolean.TRUE.equals(suite.getContinueOnFailure())) {
                    break;
                }
            }
        }
        suite.setLastRunResult(success ? "SUCCESS" : "FAILED");
        suite.setLastRunAt(LocalDateTime.now());
        suite.setUpdatedAt(LocalDateTime.now());
        suiteMapper.updateById(suite);
        persistSuiteRunHistory(suite, effectiveRequest, success, failureSummary, stepResults, reportId, enabledItems.size());
        return new ApiRunResponse(
                taskId,
                reportId,
                "Execution suite",
                suite.getSuiteName(),
                success ? "SUCCESS" : "FAILED",
                failureSummary,
                stepResults
        );
    }

    public PageResponse<ApiExecutionSuiteRunHistoryItem> listSuiteRunHistory(
            Long suiteId,
            String workspaceCode,
            Integer pageNo,
            Integer pageSize
    ) {
        ApiExecutionSuiteEntity suite = requireSuite(suiteId);
        workspaceScopeSupport.validateReadable(suite.getWorkspaceId(), workspaceCode, "Current workspace cannot access the execution suite");
        List<ApiExecutionSuiteRunHistoryItem> items = suiteRunHistoryMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteRunHistoryEntity>()
                        .eq(ApiExecutionSuiteRunHistoryEntity::getSuiteId, suiteId)
                        .orderByDesc(ApiExecutionSuiteRunHistoryEntity::getCreatedAt)
                        .orderByDesc(ApiExecutionSuiteRunHistoryEntity::getId))
                .stream()
                .map(this::toSuiteRunHistoryItem)
                .toList();
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    public ApiExecutionSuiteRunHistoryDetail getSuiteRunHistoryDetail(Long historyId, String workspaceCode) {
        ApiExecutionSuiteRunHistoryEntity history = requireSuiteRunHistory(historyId);
        workspaceScopeSupport.validateReadable(history.getWorkspaceId(), workspaceCode, "Current workspace cannot access the execution suite run history");
        return toSuiteRunHistoryDetail(history);
    }

    private List<ApiExecutionSuiteModuleItem> buildSuiteModuleTree(
            List<ApiExecutionSuiteModuleEntity> modules,
            Map<Long, Long> counts,
            Long parentId
    ) {
        return modules.stream()
                .filter(module -> parentId == null ? module.getParentId() == null : parentId.equals(module.getParentId()))
                .map(module -> {
                    List<ApiExecutionSuiteModuleItem> children = buildSuiteModuleTree(modules, counts, module.getId());
                    return toSuiteModuleItem(module, counts.getOrDefault(module.getId(), 0L), children);
                })
                .toList();
    }

    private ApiExecutionSuiteModuleItem toSuiteModuleItem(
            ApiExecutionSuiteModuleEntity entity,
            Long suiteCount,
            List<ApiExecutionSuiteModuleItem> children
    ) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiExecutionSuiteModuleItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getParentId(),
                entity.getModuleName(),
                entity.getSortOrder(),
                suiteCount,
                children
        );
    }

    private void fillSuiteEntity(ApiExecutionSuiteEntity entity, WorkspaceEntity workspace, SaveApiExecutionSuiteRequest request) {
        Long moduleId = request.moduleId();
        if (moduleId != null) {
            ApiExecutionSuiteModuleEntity module = requireSuiteModule(moduleId);
            if (!module.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Execution suite module must belong to the same workspace");
            }
        }
        entity.setWorkspaceId(workspace.getId());
        entity.setModuleId(moduleId);
        entity.setSuiteName(request.name().trim());
        entity.setPriority(normalizePriority(request.priority()));
        entity.setStatus(normalizeStatus(request.status()));
        entity.setDescription(blankToNull(request.description()));
        entity.setEnvironmentId(request.environmentId());
        entity.setVariableSetId(request.variableSetId());
        entity.setRunMode(normalizeRunMode(request.runMode()));
        entity.setRunOn(normalizeRunOn(request.runOn()));
        entity.setNotifyEnabled(request.notifyEnabled() == null || request.notifyEnabled());
        entity.setContinueOnFailure(Boolean.TRUE.equals(request.continueOnFailure()));
        entity.setGlobalTimeoutMs(normalizeRange(request.globalTimeoutMs(), 300000, 1000, 3600000));
        entity.setStepFailureRetryCount(normalizeRange(request.stepFailureRetryCount(), 0, 0, 5));
        entity.setDefaultStepWaitMs(normalizeRange(request.defaultStepWaitMs(), 0, 0, 60000));
        entity.setScheduleEnabled(Boolean.TRUE.equals(request.scheduleEnabled()));
        entity.setCronExpression(blankToNull(request.cronExpression()));
        entity.setBranchName(blankToNull(request.branchName()));
        entity.setTriggerSource(blankToNull(request.triggerSource()));
        entity.setBranchNote(blankToNull(request.branchNote()));
    }

    private ApiExecutionSuiteItem toSuiteItem(ApiExecutionSuiteEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        ApiExecutionSuiteModuleEntity module = entity.getModuleId() == null ? null : suiteModuleMapper.selectById(entity.getModuleId());
        return new ApiExecutionSuiteItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getModuleId(),
                module == null ? null : module.getModuleName(),
                entity.getSuiteName(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getEnvironmentId(),
                entity.getVariableSetId(),
                entity.getRunMode(),
                entity.getRunOn(),
                entity.getNotifyEnabled(),
                entity.getContinueOnFailure(),
                entity.getGlobalTimeoutMs(),
                entity.getStepFailureRetryCount(),
                entity.getDefaultStepWaitMs(),
                entity.getScheduleEnabled(),
                entity.getCronExpression(),
                entity.getBranchName(),
                entity.getTriggerSource(),
                entity.getBranchNote(),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiExecutionSuiteDetail toSuiteDetail(ApiExecutionSuiteEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        ApiExecutionSuiteModuleEntity module = entity.getModuleId() == null ? null : suiteModuleMapper.selectById(entity.getModuleId());
        return new ApiExecutionSuiteDetail(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getModuleId(),
                module == null ? null : module.getModuleName(),
                entity.getSuiteName(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getEnvironmentId(),
                entity.getVariableSetId(),
                entity.getRunMode(),
                entity.getRunOn(),
                entity.getNotifyEnabled(),
                entity.getContinueOnFailure(),
                entity.getGlobalTimeoutMs(),
                entity.getStepFailureRetryCount(),
                entity.getDefaultStepWaitMs(),
                entity.getScheduleEnabled(),
                entity.getCronExpression(),
                entity.getBranchName(),
                entity.getTriggerSource(),
                entity.getBranchNote(),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiExecutionSuiteItemDetail toSuiteItemDetail(ApiExecutionSuiteItemEntity entity) {
        return new ApiExecutionSuiteItemDetail(
                entity.getId(),
                entity.getSuiteId(),
                entity.getItemType(),
                entity.getItemId(),
                entity.getItemNameSnapshot(),
                entity.getSortOrder(),
                entity.getEnabled(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void persistSuiteRunHistory(
            ApiExecutionSuiteEntity suite,
            ApiRunRequest request,
            boolean success,
            String failureSummary,
            List<ApiRunStepResultResponse> stepResults,
            Long reportId,
            int totalCount
    ) {
        CurrentUserPrincipal currentUser = CurrentUserContext.require();
        long durationMs = stepResults.stream()
                .map(ApiRunStepResultResponse::durationMs)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        int successCount = (int) stepResults.stream().filter(ApiRunStepResultResponse::success).count();
        ApiExecutionSuiteRunHistoryEntity entity = new ApiExecutionSuiteRunHistoryEntity();
        entity.setWorkspaceId(suite.getWorkspaceId());
        entity.setSuiteId(suite.getId());
        entity.setSuiteName(suite.getSuiteName());
        entity.setReportId(reportId);
        entity.setResult(success ? "SUCCESS" : "FAILED");
        entity.setFailureSummary(failureSummary);
        entity.setTotalCount(totalCount);
        entity.setSuccessCount(successCount);
        entity.setFailedCount(Math.max(0, stepResults.size() - successCount));
        entity.setSkippedCount(Math.max(0, totalCount - stepResults.size()));
        entity.setDurationMs(durationMs);
        entity.setEnvironmentId(request.environmentId());
        entity.setVariableSetId(request.variableSetId());
        entity.setOperatorId(currentUser.userId());
        entity.setOperatorName(currentUser.displayName());
        entity.setDetailJson(ApiAutomationJsonSupport.toJson(stepResults, "Failed to serialize execution suite run detail"));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        suiteRunHistoryMapper.insert(entity);
    }

    private ApiExecutionSuiteRunHistoryItem toSuiteRunHistoryItem(ApiExecutionSuiteRunHistoryEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new ApiExecutionSuiteRunHistoryItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getSuiteId(),
                entity.getSuiteName(),
                entity.getReportId(),
                entity.getResult(),
                entity.getFailureSummary(),
                entity.getTotalCount(),
                entity.getSuccessCount(),
                entity.getFailedCount(),
                entity.getSkippedCount(),
                entity.getDurationMs(),
                entity.getEnvironmentId(),
                entity.getVariableSetId(),
                entity.getOperatorId(),
                entity.getOperatorName(),
                entity.getCreatedAt()
        );
    }

    private ApiExecutionSuiteRunHistoryDetail toSuiteRunHistoryDetail(ApiExecutionSuiteRunHistoryEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        List<ApiRunStepResultResponse> stepResults = ApiAutomationJsonSupport.readList(
                entity.getDetailJson(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                },
                List.of()
        );
        return new ApiExecutionSuiteRunHistoryDetail(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getSuiteId(),
                entity.getSuiteName(),
                entity.getReportId(),
                entity.getResult(),
                entity.getFailureSummary(),
                entity.getTotalCount(),
                entity.getSuccessCount(),
                entity.getFailedCount(),
                entity.getSkippedCount(),
                entity.getDurationMs(),
                entity.getEnvironmentId(),
                entity.getVariableSetId(),
                entity.getOperatorId(),
                entity.getOperatorName(),
                entity.getCreatedAt(),
                stepResults
        );
    }

    private ResolvedSuiteItem resolveSuiteItem(Long workspaceId, String itemType, Long itemId) {
        String normalizedType = itemType == null ? "" : itemType.trim().toUpperCase(Locale.ROOT);
        if ("API_CASE".equals(normalizedType)) {
            ApiDefinitionCaseEntity apiCase = caseMapper.selectById(itemId);
            if (apiCase == null || !workspaceId.equals(apiCase.getWorkspaceId())) {
                throw new BadRequestException("API case must belong to the same workspace");
            }
            return new ResolvedSuiteItem(normalizedType, apiCase.getCaseName());
        }
        if ("SCENARIO".equals(normalizedType)) {
            ApiScenarioEntity scenario = scenarioMapper.selectById(itemId);
            if (scenario == null || !workspaceId.equals(scenario.getWorkspaceId())) {
                throw new BadRequestException("Scenario must belong to the same workspace");
            }
            return new ResolvedSuiteItem(normalizedType, scenario.getScenarioName());
        }
        throw new BadRequestException("Unsupported execution suite item type");
    }

    private int nextSuiteItemSort(Long suiteId) {
        List<ApiExecutionSuiteItemEntity> siblings = suiteItemMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteItemEntity>()
                .eq(ApiExecutionSuiteItemEntity::getSuiteId, suiteId)
                .orderByDesc(ApiExecutionSuiteItemEntity::getSortOrder));
        return siblings.isEmpty() ? 0 : siblings.getFirst().getSortOrder() + 10;
    }

    private String normalizePriority(String priority) {
        String normalized = priority == null || priority.isBlank() ? "P1" : priority.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P0", "P1", "P2", "P3" -> normalized;
            default -> "P1";
        };
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "INACTIVE", "ARCHIVED" -> normalized;
            default -> "ACTIVE";
        };
    }

    private String normalizeRunMode(String runMode) {
        String normalized = runMode == null || runMode.isBlank() ? "SERIAL" : runMode.trim().toUpperCase(Locale.ROOT);
        return "PARALLEL".equals(normalized) ? "PARALLEL" : "SERIAL";
    }

    private String normalizeRunOn(String runOn) {
        String normalized = runOn == null || runOn.isBlank() ? "LOCAL" : runOn.trim().toUpperCase(Locale.ROOT);
        return "REMOTE".equals(normalized) ? "REMOTE" : "LOCAL";
    }

    private Integer normalizeRange(Integer value, int fallback, int min, int max) {
        int normalized = value == null ? fallback : value;
        if (normalized < min) {
            return min;
        }
        return Math.min(normalized, max);
    }

    private void ensureSuiteModuleNameUnique(Long workspaceId, Long parentId, Long currentId, String name) {
        String trimmedName = blankToNull(name);
        if (trimmedName == null) {
            throw new BadRequestException("Module name cannot be blank");
        }
        LambdaQueryWrapper<ApiExecutionSuiteModuleEntity> query = new LambdaQueryWrapper<ApiExecutionSuiteModuleEntity>()
                .eq(ApiExecutionSuiteModuleEntity::getWorkspaceId, workspaceId)
                .eq(parentId != null, ApiExecutionSuiteModuleEntity::getParentId, parentId)
                .isNull(parentId == null, ApiExecutionSuiteModuleEntity::getParentId)
                .eq(ApiExecutionSuiteModuleEntity::getModuleName, trimmedName);
        if (currentId != null) {
            query.ne(ApiExecutionSuiteModuleEntity::getId, currentId);
        }
        if (suiteModuleMapper.selectCount(query) > 0) {
            throw new BadRequestException("Module name already exists");
        }
    }

    private int nextSuiteModuleSort(Long workspaceId, Long parentId) {
        List<ApiExecutionSuiteModuleEntity> siblings = suiteModuleMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteModuleEntity>()
                .eq(ApiExecutionSuiteModuleEntity::getWorkspaceId, workspaceId)
                .eq(parentId != null, ApiExecutionSuiteModuleEntity::getParentId, parentId)
                .isNull(parentId == null, ApiExecutionSuiteModuleEntity::getParentId)
                .orderByDesc(ApiExecutionSuiteModuleEntity::getSortOrder));
        return siblings.isEmpty() ? 0 : siblings.getFirst().getSortOrder() + 10;
    }

    private long countSuitesInModule(Long moduleId) {
        return suiteMapper.selectCount(new LambdaQueryWrapper<ApiExecutionSuiteEntity>()
                .eq(ApiExecutionSuiteEntity::getModuleId, moduleId));
    }

    private List<Long> suiteModuleDescendantIds(Long workspaceId, Long moduleId) {
        List<ApiExecutionSuiteModuleEntity> modules = suiteModuleMapper.selectList(new LambdaQueryWrapper<ApiExecutionSuiteModuleEntity>()
                .eq(ApiExecutionSuiteModuleEntity::getWorkspaceId, workspaceId));
        List<Long> ids = new ArrayList<>();
        collectSuiteModuleDescendantIds(modules, moduleId, ids);
        return ids.isEmpty() ? List.of(moduleId) : ids;
    }

    private void collectSuiteModuleDescendantIds(List<ApiExecutionSuiteModuleEntity> modules, Long parentId, List<Long> ids) {
        ids.add(parentId);
        for (ApiExecutionSuiteModuleEntity module : modules) {
            if (parentId.equals(module.getParentId())) {
                collectSuiteModuleDescendantIds(modules, module.getId(), ids);
            }
        }
    }

    private ApiExecutionSuiteModuleEntity requireSuiteModule(Long id) {
        ApiExecutionSuiteModuleEntity entity = suiteModuleMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Execution suite module not found");
        }
        return entity;
    }

    private ApiExecutionSuiteEntity requireSuite(Long id) {
        ApiExecutionSuiteEntity entity = suiteMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Execution suite not found");
        }
        return entity;
    }

    private ApiExecutionSuiteItemEntity requireSuiteItem(Long suiteId, Long itemId) {
        ApiExecutionSuiteItemEntity entity = suiteItemMapper.selectById(itemId);
        if (entity == null || !suiteId.equals(entity.getSuiteId())) {
            throw new NotFoundException("Execution suite item not found");
        }
        return entity;
    }

    private ApiExecutionSuiteRunHistoryEntity requireSuiteRunHistory(Long id) {
        ApiExecutionSuiteRunHistoryEntity entity = suiteRunHistoryMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Execution suite run history not found");
        }
        return entity;
    }

    private record ResolvedSuiteItem(String itemType, String itemName) {
    }
}
