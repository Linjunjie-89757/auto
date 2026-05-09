package com.company.autoplatform.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SettingsService {

    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final WorkspaceService workspaceService;

    public SettingsService(EnvConfigMapper envConfigMapper, ParamSetMapper paramSetMapper, WorkspaceService workspaceService) {
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.workspaceService = workspaceService;
    }

    public PageResponse<EnvConfigItem> listEnvs(String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<EnvConfigEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(EnvConfigEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(EnvConfigEntity::getWorkspaceId, workspaceIds);
        }
        var items = envConfigMapper.selectList(query.orderByAsc(EnvConfigEntity::getId)).stream()
                .map(this::toEnvItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public EnvConfigItem createEnv(String headerWorkspaceCode, CreateEnvConfigRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        EnvConfigEntity entity = new EnvConfigEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setEnvType(request.envType());
        entity.setEnvName(request.envName());
        entity.setBaseUrl(request.baseUrl());
        entity.setConfigJson(request.configJson());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.insert(entity);
        return toEnvItem(entity);
    }

    public EnvConfigItem updateEnv(Long id, String headerWorkspaceCode, CreateEnvConfigRequest request) {
        EnvConfigEntity entity = requireEnv(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "当前空间上下文不可编辑该环境");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改环境归属空间");
        }
        entity.setEnvType(request.envType());
        entity.setEnvName(request.envName());
        entity.setBaseUrl(request.baseUrl());
        entity.setConfigJson(request.configJson());
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.updateById(entity);
        return toEnvItem(entity);
    }

    public EnvConfigItem updateEnvStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        EnvConfigEntity entity = requireEnv(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可修改该环境");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.updateById(entity);
        return toEnvItem(entity);
    }

    public void deleteEnv(Long id, String workspaceCode) {
        EnvConfigEntity entity = requireEnv(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可删除该环境");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        envConfigMapper.deleteById(id);
    }

    public PageResponse<ParamSetItem> listParams(String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<ParamSetEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(ParamSetEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(ParamSetEntity::getWorkspaceId, workspaceIds);
        }
        var items = paramSetMapper.selectList(query.orderByAsc(ParamSetEntity::getId)).stream()
                .map(this::toParamItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ParamSetItem createParam(String headerWorkspaceCode, CreateParamSetRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        ParamSetEntity entity = new ParamSetEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setParamType(request.paramType());
        entity.setParamName(request.paramName());
        entity.setContentJson(request.contentJson());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.insert(entity);
        return toParamItem(entity);
    }

    public ParamSetItem updateParam(Long id, String headerWorkspaceCode, CreateParamSetRequest request) {
        ParamSetEntity entity = requireParam(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "当前空间上下文不可编辑该参数集");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改参数集归属空间");
        }
        entity.setParamType(request.paramType());
        entity.setParamName(request.paramName());
        entity.setContentJson(request.contentJson());
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.updateById(entity);
        return toParamItem(entity);
    }

    public ParamSetItem updateParamStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        ParamSetEntity entity = requireParam(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可修改该参数集");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.updateById(entity);
        return toParamItem(entity);
    }

    public void deleteParam(Long id, String workspaceCode) {
        ParamSetEntity entity = requireParam(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可删除该参数集");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        paramSetMapper.deleteById(id);
    }

    private WorkspaceEntity resolveScopedWorkspace(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        return WorkspaceScope.isAll(normalized) ? null : workspaceService.requireReadableWorkspace(normalized);
    }

    private EnvConfigEntity requireEnv(Long id) {
        EnvConfigEntity entity = envConfigMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("环境不存在");
        }
        return entity;
    }

    private ParamSetEntity requireParam(Long id) {
        ParamSetEntity entity = paramSetMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("参数集不存在");
        }
        return entity;
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

    private Integer normalizeStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BadRequestException("状态只能是 0 或 1");
        }
        return status;
    }

    private EnvConfigItem toEnvItem(EnvConfigEntity item) {
        WorkspaceEntity currentWorkspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new EnvConfigItem(
                item.getId(),
                currentWorkspace.getWorkspaceCode(),
                currentWorkspace.getWorkspaceName(),
                item.getEnvType(),
                item.getEnvName(),
                item.getBaseUrl(),
                item.getConfigJson(),
                item.getStatus()
        );
    }

    private ParamSetItem toParamItem(ParamSetEntity item) {
        WorkspaceEntity currentWorkspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new ParamSetItem(
                item.getId(),
                currentWorkspace.getWorkspaceCode(),
                currentWorkspace.getWorkspaceName(),
                item.getParamType(),
                item.getParamName(),
                item.getContentJson(),
                item.getStatus()
        );
    }
}
