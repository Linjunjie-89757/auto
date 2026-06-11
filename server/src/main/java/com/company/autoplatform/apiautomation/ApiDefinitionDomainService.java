package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static com.company.autoplatform.apiautomation.ApiAutomationFormatSupport.*;

@Service
public class ApiDefinitionDomainService {

    private static final String SCENARIO_RESOURCE_TYPE_DEFINITION = "DEFINITION";

    private final ApiDefinitionMapper definitionMapper;
    private final ApiDefinitionModuleMapper definitionModuleMapper;
    private final ApiDefinitionCaseMapper caseMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;

    public ApiDefinitionDomainService(
            ApiDefinitionMapper definitionMapper,
            ApiDefinitionModuleMapper definitionModuleMapper,
            ApiDefinitionCaseMapper caseMapper,
            ApiScenarioMapper scenarioMapper,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport
    ) {
        this.definitionMapper = definitionMapper;
        this.definitionModuleMapper = definitionModuleMapper;
        this.caseMapper = caseMapper;
        this.scenarioMapper = scenarioMapper;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
    }

    public PageResponse<ApiDefinitionItem> listDefinitions(
            String workspaceCode,
            String keyword,
            Long moduleId,
            Integer pageNo,
            Integer pageSize
    ) {
        LambdaQueryWrapper<ApiDefinitionEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, ApiDefinitionEntity::getWorkspaceId, workspaceCode);
        String trimmedKeyword = blankToNull(keyword);
        if (trimmedKeyword != null) {
            query.and(wrapper -> wrapper
                    .like(ApiDefinitionEntity::getDefinitionName, trimmedKeyword)
                    .or()
                    .like(ApiDefinitionEntity::getPath, trimmedKeyword)
                    .or()
                    .like(ApiDefinitionEntity::getHttpMethod, trimmedKeyword)
                    .or()
                    .like(ApiDefinitionEntity::getDirectoryName, trimmedKeyword)
                    .or()
                    .like(ApiDefinitionEntity::getTagsJson, trimmedKeyword));
        }
        String modulePath = resolveDefinitionModulePath(moduleId, workspaceCode);
        List<ApiDefinitionItem> items = definitionMapper.selectList(query.orderByDesc(ApiDefinitionEntity::getUpdatedAt))
                .stream()
                .filter(definition -> matchesDefinitionModule(definition, modulePath))
                .map(this::toDefinitionItem)
                .toList();
        int safePageNo = safePageNo(pageNo);
        int safePageSize = safePageSize(pageSize, items.size());
        return PageResponse.of(paginate(items, safePageNo, safePageSize), items.size(), safePageNo, safePageSize);
    }

    public ApiDefinitionDetail getDefinition(Long id, String workspaceCode) {
        ApiDefinitionEntity entity = requireDefinition(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the definition");
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
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the definition");
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
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the definition");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        long caseCount = caseMapper.selectCount(new LambdaQueryWrapper<ApiDefinitionCaseEntity>()
                .eq(ApiDefinitionCaseEntity::getDefinitionId, id));
        if (caseCount > 0) {
            throw new BadRequestException("This definition is still referenced by cases");
        }
        long scenarioCount = countScenarioReferences(entity.getWorkspaceId(), SCENARIO_RESOURCE_TYPE_DEFINITION, id);
        if (scenarioCount > 0) {
            throw new BadRequestException("This definition is still referenced by scenarios");
        }
        definitionMapper.deleteById(id);
    }

    public List<ApiDefinitionModuleItem> listDefinitionModules(String workspaceCode) {
        ensureDefinitionModulesFromDefinitions(workspaceCode);
        WorkspaceEntity scopedWorkspace = workspaceScopeSupport.resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<>();
        if (scopedWorkspace != null) {
            query.eq(ApiDefinitionModuleEntity::getWorkspaceId, scopedWorkspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            query.in(ApiDefinitionModuleEntity::getWorkspaceId, workspaceIds.isEmpty() ? List.of(-1L) : workspaceIds);
        }
        List<ApiDefinitionModuleEntity> modules = definitionModuleMapper.selectList(query
                .orderByAsc(ApiDefinitionModuleEntity::getSortOrder)
                .orderByAsc(ApiDefinitionModuleEntity::getId));
        Map<Long, String> pathMap = buildDefinitionModulePathMap(modules);
        Set<Long> moduleWorkspaceIds = modules.stream()
                .map(ApiDefinitionModuleEntity::getWorkspaceId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, Long> counts = new HashMap<>();
        definitionMapper.selectList(new LambdaQueryWrapper<ApiDefinitionEntity>())
                .stream()
                .filter(definition -> moduleWorkspaceIds.contains(definition.getWorkspaceId()))
                .filter(definition -> blankToNull(definition.getDirectoryName()) != null)
                .forEach(definition -> {
                    Long moduleId = findDefinitionModuleIdByPath(modules, pathMap, definition.getWorkspaceId(), definition.getDirectoryName());
                    if (moduleId != null) {
                        counts.merge(moduleId, 1L, Long::sum);
                    }
                });
        return buildDefinitionModuleTree(modules, pathMap, counts, null);
    }

    public ApiDefinitionModuleItem createDefinitionModule(String headerWorkspaceCode, ApiDefinitionModuleRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (request.parentId() != null) {
            ApiDefinitionModuleEntity parent = requireDefinitionModule(request.parentId());
            if (!parent.getWorkspaceId().equals(workspace.getId())) {
                throw new BadRequestException("Parent module must belong to the same workspace");
            }
        }
        ensureDefinitionModuleNameUnique(workspace.getId(), request.parentId(), null, request.name());
        ApiDefinitionModuleEntity entity = new ApiDefinitionModuleEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setParentId(request.parentId());
        entity.setModuleName(request.name().trim());
        entity.setSortOrder(nextDefinitionModuleSort(workspace.getId(), request.parentId()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        definitionModuleMapper.insert(entity);
        return toDefinitionModuleItem(entity, currentDefinitionModulePathMap(entity.getWorkspaceId()), 0L, List.of());
    }

    public ApiDefinitionModuleItem updateDefinitionModule(Long id, String workspaceCode, ApiDefinitionModuleRequest request) {
        ApiDefinitionModuleEntity entity = requireDefinitionModule(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot edit the definition module");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        String previousPath = getDefinitionModulePath(entity);
        ensureDefinitionModuleNameUnique(entity.getWorkspaceId(), entity.getParentId(), id, request.name());
        entity.setModuleName(request.name().trim());
        entity.setUpdatedAt(LocalDateTime.now());
        definitionModuleMapper.updateById(entity);
        String nextPath = getDefinitionModulePath(entity);
        syncDefinitionDirectoryPrefix(entity.getWorkspaceId(), previousPath, nextPath);
        return toDefinitionModuleItem(entity, currentDefinitionModulePathMap(entity.getWorkspaceId()), countDefinitionsInModulePath(entity.getWorkspaceId(), nextPath), List.of());
    }

    public ApiDefinitionModuleItem moveDefinitionModule(Long id, String workspaceCode, MoveApiDefinitionModuleRequest request) {
        ApiDefinitionModuleEntity entity = requireDefinitionModule(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot move the definition module");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        String previousPath = getDefinitionModulePath(entity);
        if (request.parentId() != null) {
            ApiDefinitionModuleEntity parent = requireDefinitionModule(request.parentId());
            if (!parent.getWorkspaceId().equals(entity.getWorkspaceId())) {
                throw new BadRequestException("Parent module must belong to the same workspace");
            }
            if (definitionModuleDescendantIds(entity.getWorkspaceId(), id).contains(request.parentId())) {
                throw new BadRequestException("Cannot move module under itself");
            }
        }
        ensureDefinitionModuleNameUnique(entity.getWorkspaceId(), request.parentId(), id, entity.getModuleName());
        entity.setParentId(request.parentId());
        entity.setSortOrder(request.sortOrder() == null ? nextDefinitionModuleSort(entity.getWorkspaceId(), request.parentId()) : request.sortOrder());
        entity.setUpdatedAt(LocalDateTime.now());
        definitionModuleMapper.updateById(entity);
        String nextPath = getDefinitionModulePath(entity);
        syncDefinitionDirectoryPrefix(entity.getWorkspaceId(), previousPath, nextPath);
        return toDefinitionModuleItem(entity, currentDefinitionModulePathMap(entity.getWorkspaceId()), countDefinitionsInModulePath(entity.getWorkspaceId(), nextPath), List.of());
    }

    public void deleteDefinitionModule(Long id, String workspaceCode) {
        ApiDefinitionModuleEntity entity = requireDefinitionModule(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the definition module");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        if (definitionModuleMapper.selectCount(new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getParentId, id)) > 0) {
            throw new BadRequestException("Cannot delete a module that contains child modules");
        }
        syncDefinitionDirectoryPrefix(entity.getWorkspaceId(), getDefinitionModulePath(entity), "");
        definitionModuleMapper.deleteById(id);
    }

    private void fillDefinitionEntity(ApiDefinitionEntity entity, WorkspaceEntity workspace, SaveApiDefinitionRequest request) {
        entity.setWorkspaceId(workspace.getId());
        entity.setDefinitionName(request.name().trim());
        entity.setHttpMethod(request.requestConfig().method().trim().toUpperCase());
        entity.setPath(request.requestConfig().path().trim());
        entity.setDirectoryName(blankToNull(request.directoryName()));
        ensureDefinitionModulePath(workspace.getId(), entity.getDirectoryName());
        entity.setDescription(blankToNull(request.description()));
        entity.setTagsJson(ApiAutomationJsonSupport.toJson(defaultList(request.tags()), "Failed to serialize tags"));
        entity.setRequestJson(ApiAutomationJsonSupport.toJson(request.requestConfig(), "Failed to serialize request config"));
        entity.setAssertionsJson(ApiAutomationJsonSupport.toJson(defaultList(request.assertions()), "Failed to serialize assertions"));
        entity.setExtractorsJson(ApiAutomationJsonSupport.toJson(defaultList(request.extractors()), "Failed to serialize extractors"));
        entity.setPreprocessorsJson(ApiAutomationJsonSupport.toJson(normalizeProcessors(request.preProcessors(), "PRE"),
                "Failed to serialize pre-processors"));
        entity.setPostprocessorsJson(ApiAutomationJsonSupport.toJson(normalizePostProcessors(request.postProcessors(), request.extractors()),
                "Failed to serialize post-processors"));
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
                readExtractors(entity.getExtractorsJson()),
                readProcessorsJson(entity.getPreprocessorsJson()),
                readProcessorsJson(entity.getPostprocessorsJson()),
                entity.getLastRunResult(),
                entity.getLastRunAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ApiDefinitionModuleEntity requireDefinitionModule(Long id) {
        ApiDefinitionModuleEntity entity = definitionModuleMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API definition module not found");
        }
        return entity;
    }

    private ApiDefinitionEntity requireDefinition(Long id) {
        ApiDefinitionEntity entity = definitionMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("API definition not found");
        }
        return entity;
    }

    private List<ApiDefinitionModuleItem> buildDefinitionModuleTree(
            List<ApiDefinitionModuleEntity> modules,
            Map<Long, String> pathMap,
            Map<Long, Long> counts,
            Long parentId
    ) {
        return modules.stream()
                .filter(module -> parentId == null ? module.getParentId() == null : parentId.equals(module.getParentId()))
                .map(module -> toDefinitionModuleItem(module, pathMap, counts.getOrDefault(module.getId(), 0L),
                        buildDefinitionModuleTree(modules, pathMap, counts, module.getId())))
                .toList();
    }

    private ApiDefinitionModuleItem toDefinitionModuleItem(
            ApiDefinitionModuleEntity entity,
            Map<Long, String> pathMap,
            Long definitionCount,
            List<ApiDefinitionModuleItem> children
    ) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        String path = pathMap.getOrDefault(entity.getId(), entity.getModuleName());
        return new ApiDefinitionModuleItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getParentId(),
                entity.getModuleName(),
                path,
                entity.getSortOrder(),
                definitionCount,
                children
        );
    }

    private Map<Long, String> currentDefinitionModulePathMap(Long workspaceId) {
        return buildDefinitionModulePathMap(definitionModuleMapper.selectList(new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId)));
    }

    private Map<Long, String> buildDefinitionModulePathMap(List<ApiDefinitionModuleEntity> modules) {
        Map<Long, ApiDefinitionModuleEntity> moduleMap = modules.stream()
                .collect(java.util.stream.Collectors.toMap(ApiDefinitionModuleEntity::getId, item -> item));
        Map<Long, String> pathMap = new HashMap<>();
        for (ApiDefinitionModuleEntity module : modules) {
            pathMap.put(module.getId(), buildDefinitionModulePath(module, moduleMap));
        }
        return pathMap;
    }

    private String buildDefinitionModulePath(ApiDefinitionModuleEntity module, Map<Long, ApiDefinitionModuleEntity> moduleMap) {
        List<String> names = new java.util.ArrayList<>();
        ApiDefinitionModuleEntity current = module;
        while (current != null) {
            names.add(current.getModuleName());
            current = current.getParentId() == null ? null : moduleMap.get(current.getParentId());
        }
        java.util.Collections.reverse(names);
        return String.join("/", names);
    }

    private String getDefinitionModulePath(ApiDefinitionModuleEntity module) {
        return currentDefinitionModulePathMap(module.getWorkspaceId()).getOrDefault(module.getId(), module.getModuleName());
    }

    private Long findDefinitionModuleIdByPath(
            List<ApiDefinitionModuleEntity> modules,
            Map<Long, String> pathMap,
            Long workspaceId,
            String path
    ) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return modules.stream()
                .filter(module -> module.getWorkspaceId().equals(workspaceId))
                .filter(module -> path.equals(pathMap.get(module.getId())))
                .map(ApiDefinitionModuleEntity::getId)
                .findFirst()
                .orElse(null);
    }

    private void ensureDefinitionModuleNameUnique(Long workspaceId, Long parentId, Long excludeId, String name) {
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId)
                .eq(ApiDefinitionModuleEntity::getModuleName, name.trim());
        if (parentId == null) {
            query.isNull(ApiDefinitionModuleEntity::getParentId);
        } else {
            query.eq(ApiDefinitionModuleEntity::getParentId, parentId);
        }
        if (excludeId != null) {
            query.ne(ApiDefinitionModuleEntity::getId, excludeId);
        }
        if (definitionModuleMapper.selectCount(query) > 0) {
            throw new BadRequestException("Module name already exists");
        }
    }

    private int nextDefinitionModuleSort(Long workspaceId, Long parentId) {
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId);
        if (parentId == null) {
            query.isNull(ApiDefinitionModuleEntity::getParentId);
        } else {
            query.eq(ApiDefinitionModuleEntity::getParentId, parentId);
        }
        return definitionModuleMapper.selectList(query).stream()
                .map(ApiDefinitionModuleEntity::getSortOrder)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void ensureDefinitionModulesFromDefinitions(String workspaceCode) {
        LambdaQueryWrapper<ApiDefinitionEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, ApiDefinitionEntity::getWorkspaceId, workspaceCode);
        definitionMapper.selectList(query).stream()
                .filter(definition -> blankToNull(definition.getDirectoryName()) != null)
                .forEach(definition -> ensureDefinitionModulePath(definition.getWorkspaceId(), definition.getDirectoryName()));
    }

    private void ensureDefinitionModulePath(Long workspaceId, String directoryName) {
        if (directoryName == null || directoryName.isBlank()) {
            return;
        }
        Long parentId = null;
        for (String rawPart : directoryName.split("/")) {
            String part = rawPart.trim();
            if (part.isBlank()) {
                continue;
            }
            ApiDefinitionModuleEntity existing = findDefinitionModuleByName(workspaceId, parentId, part);
            if (existing != null) {
                parentId = existing.getId();
                continue;
            }
            ApiDefinitionModuleEntity entity = new ApiDefinitionModuleEntity();
            entity.setWorkspaceId(workspaceId);
            entity.setParentId(parentId);
            entity.setModuleName(part);
            entity.setSortOrder(nextDefinitionModuleSort(workspaceId, parentId));
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            definitionModuleMapper.insert(entity);
            parentId = entity.getId();
        }
    }

    private ApiDefinitionModuleEntity findDefinitionModuleByName(Long workspaceId, Long parentId, String name) {
        LambdaQueryWrapper<ApiDefinitionModuleEntity> query = new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId)
                .eq(ApiDefinitionModuleEntity::getModuleName, name);
        if (parentId == null) {
            query.isNull(ApiDefinitionModuleEntity::getParentId);
        } else {
            query.eq(ApiDefinitionModuleEntity::getParentId, parentId);
        }
        return definitionModuleMapper.selectList(query).stream().findFirst().orElse(null);
    }

    private long countDefinitionsInModulePath(Long workspaceId, String modulePath) {
        return definitionMapper.selectList(new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getWorkspaceId, workspaceId))
                .stream()
                .filter(definition -> modulePath.equals(definition.getDirectoryName())
                        || (definition.getDirectoryName() != null && definition.getDirectoryName().startsWith(modulePath + "/")))
                .count();
    }

    private void syncDefinitionDirectoryPrefix(Long workspaceId, String sourcePath, String targetPath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return;
        }
        definitionMapper.selectList(new LambdaQueryWrapper<ApiDefinitionEntity>()
                        .eq(ApiDefinitionEntity::getWorkspaceId, workspaceId))
                .forEach(definition -> {
                    String directory = definition.getDirectoryName();
                    if (directory == null) {
                        return;
                    }
                    if (directory.equals(sourcePath) || directory.startsWith(sourcePath + "/")) {
                        String suffix = directory.length() == sourcePath.length() ? "" : directory.substring(sourcePath.length());
                        definition.setDirectoryName(targetPath == null || targetPath.isBlank() ? blankToNull(suffix.replaceFirst("^/", "")) : targetPath + suffix);
                        definition.setUpdatedAt(LocalDateTime.now());
                        definitionMapper.updateById(definition);
                    }
                });
    }

    private long countScenarioReferences(Long workspaceId, String resourceType, Long resourceId) {
        return scenarioMapper.selectList(new LambdaQueryWrapper<ApiScenarioEntity>()
                        .eq(ApiScenarioEntity::getWorkspaceId, workspaceId))
                .stream()
                .filter(entity -> containsScenarioReference(readScenarioSteps(entity.getStepsJson()), resourceType, resourceId))
                .count();
    }

    private boolean containsScenarioReference(List<ApiScenarioStepInput> steps, String resourceType, Long resourceId) {
        for (ApiScenarioStepInput step : defaultList(steps)) {
            if (step == null) {
                continue;
            }
            if (resourceId.equals(normalizeScenarioResourceId(step))
                    && resourceType.equals(normalizeScenarioResourceTypeForStep(normalizeScenarioStepType(step)))) {
                return true;
            }
            if (containsScenarioReference(step.children(), resourceType, resourceId)) {
                return true;
            }
        }
        return false;
    }

    private List<ApiScenarioStepInput> readScenarioSteps(String json) {
        return ApiAutomationJsonSupport.readList(json, new TypeReference<>() {
        }, List.of());
    }

    private String normalizeScenarioStepType(ApiScenarioStepInput step) {
        String rawType = blankToNull(step.stepType());
        if (rawType != null) {
            return rawType.toUpperCase();
        }
        String resourceType = Optional.ofNullable(step.resourceType()).orElse("").trim().toUpperCase();
        if ("CASE".equals(resourceType)) {
            return "API_CASE";
        }
        if ("SCENARIO".equals(resourceType)) {
            return "API_SCENARIO";
        }
        return "API";
    }

    private String normalizeScenarioResourceTypeForStep(String stepType) {
        return switch (stepType) {
            case "API_CASE" -> "CASE";
            case "API_SCENARIO" -> "SCENARIO";
            default -> "DEFINITION";
        };
    }

    private Long normalizeScenarioResourceId(ApiScenarioStepInput step) {
        return step.resourceId();
    }

    private List<Long> definitionModuleDescendantIds(Long workspaceId, Long moduleId) {
        List<ApiDefinitionModuleEntity> modules = definitionModuleMapper.selectList(new LambdaQueryWrapper<ApiDefinitionModuleEntity>()
                .eq(ApiDefinitionModuleEntity::getWorkspaceId, workspaceId));
        List<Long> ids = new java.util.ArrayList<>();
        collectDefinitionModuleDescendantIds(modules, moduleId, ids);
        return ids;
    }

    private void collectDefinitionModuleDescendantIds(List<ApiDefinitionModuleEntity> modules, Long parentId, List<Long> ids) {
        for (ApiDefinitionModuleEntity module : modules) {
            if (parentId.equals(module.getParentId())) {
                ids.add(module.getId());
                collectDefinitionModuleDescendantIds(modules, module.getId(), ids);
            }
        }
    }

    private String resolveDefinitionModulePath(Long moduleId, String workspaceCode) {
        if (moduleId == null) {
            return null;
        }
        ApiDefinitionModuleEntity module = requireDefinitionModule(moduleId);
        workspaceScopeSupport.validateReadable(module.getWorkspaceId(), workspaceCode, "Current workspace cannot access the definition module");
        return getDefinitionModulePath(module);
    }

    private boolean matchesDefinitionModule(ApiDefinitionEntity definition, String modulePath) {
        if (modulePath == null || modulePath.isBlank()) {
            return true;
        }
        String directory = definition.getDirectoryName();
        return modulePath.equals(directory) || (directory != null && directory.startsWith(modulePath + "/"));
    }

    private int safePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int safePageSize(Integer pageSize, int total) {
        if (pageSize == null || pageSize < 1) {
            return total > 0 ? total : 10;
        }
        return pageSize;
    }

    private <T> List<T> paginate(List<T> items, int pageNo, int pageSize) {
        int fromIndex = Math.min((pageNo - 1) * pageSize, items.size());
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        return items.subList(fromIndex, toIndex);
    }
}

