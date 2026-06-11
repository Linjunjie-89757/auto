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
import java.util.Locale;

@Service
public class SettingsParamSetDomainService {

    private final ParamSetMapper paramSetMapper;
    private final WorkspaceService workspaceService;

    public SettingsParamSetDomainService(
            ParamSetMapper paramSetMapper,
            WorkspaceService workspaceService
    ) {
        this.paramSetMapper = paramSetMapper;
        this.workspaceService = workspaceService;
    }

    public PageResponse<ParamSetItem> listParams(String workspaceCode, String keyword, String paramType, Integer status) {
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
        String trimmedKeyword = blankToNull(keyword);
        if (trimmedKeyword != null) {
            query.and(wrapper -> wrapper
                    .like(ParamSetEntity::getParamName, trimmedKeyword)
                    .or()
                    .like(ParamSetEntity::getContentJson, trimmedKeyword));
        }
        String trimmedParamType = blankToNull(paramType);
        if (trimmedParamType != null) {
            query.eq(ParamSetEntity::getParamType, trimmedParamType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null) {
            query.eq(ParamSetEntity::getStatus, normalizeStatus(status));
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
