package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.apiautomation.ApiWorkspaceScopeSupport;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.settings.EnvConfigEntity;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetEntity;
import com.company.autoplatform.settings.ParamSetMapper;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.*;
import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.*;

@Service
public class WebUiEnvironmentDomainService {

    private final WebUiEnvironmentMapper environmentMapper;
    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;
    private final WebUiExecutionContextSupport executionContextSupport;

    public WebUiEnvironmentDomainService(
            WebUiEnvironmentMapper environmentMapper,
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport,
            WebUiExecutionContextSupport executionContextSupport
    ) {
        this.environmentMapper = environmentMapper;
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
        this.executionContextSupport = executionContextSupport;
    }

    public PageResponse<WebUiEnvironmentItem> listEnvironments(String workspaceCode) {
        LambdaQueryWrapper<WebUiEnvironmentEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, WebUiEnvironmentEntity::getWorkspaceId, workspaceCode);
        List<WebUiEnvironmentItem> items = new ArrayList<>(environmentMapper.selectList(query.orderByDesc(WebUiEnvironmentEntity::getUpdatedAt))
                .stream()
                .map(this::toEnvironmentItem)
                .toList());
        LambdaQueryWrapper<EnvConfigEntity> publicQuery = new LambdaQueryWrapper<>();
        publicQuery.eq(EnvConfigEntity::getEnvType, WebUiExecutionContextSupport.WEB_UI_ENV_TYPE);
        workspaceScopeSupport.applyWorkspaceScope(publicQuery, EnvConfigEntity::getWorkspaceId, workspaceCode);
        items.addAll(envConfigMapper.selectList(publicQuery.orderByDesc(EnvConfigEntity::getUpdatedAt))
                .stream()
                .map(this::toPublicEnvironmentItem)
                .toList());
        return PageResponse.of(items, items.size(), 1, items.isEmpty() ? 1 : items.size());
    }

    public WebUiEnvironmentItem createEnvironment(String headerWorkspaceCode, SaveWebUiEnvironmentRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        WebUiEnvironmentEntity entity = new WebUiEnvironmentEntity();
        fillEnvironmentEntity(entity, workspace.getId(), request);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        environmentMapper.insert(entity);
        return toEnvironmentItem(entity);
    }

    public WebUiEnvironmentItem updateEnvironment(Long id, String headerWorkspaceCode, SaveWebUiEnvironmentRequest request) {
        WebUiEnvironmentEntity entity = requireEnvironment(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the web UI environment");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Cannot move a web UI environment to another workspace");
        }
        fillEnvironmentEntity(entity, workspace.getId(), request);
        entity.setUpdatedAt(LocalDateTime.now());
        environmentMapper.updateById(entity);
        return toEnvironmentItem(entity);
    }

    public void deleteEnvironment(Long id, String workspaceCode) {
        WebUiEnvironmentEntity entity = requireEnvironment(id);
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the web UI environment");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        environmentMapper.deleteById(id);
    }

    private void fillEnvironmentEntity(WebUiEnvironmentEntity entity, Long workspaceId, SaveWebUiEnvironmentRequest request) {
        entity.setWorkspaceId(workspaceId);
        entity.setEnvironmentName(request.environmentName().trim());
        entity.setBaseUrl(request.baseUrl().trim());
        entity.setBrowserType(normalizeBrowserType(request.browserType()));
        entity.setHeadless(request.headless() == null || request.headless());
        entity.setDefaultTimeoutMs(normalizeCaseTimeout(request.defaultTimeoutMs()));
        entity.setStatus(normalizeEnvironmentStatus(request.status()));
    }

    private WebUiEnvironmentEntity requireEnvironment(Long id) {
        WebUiEnvironmentEntity entity = environmentMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Web UI environment not found");
        }
        return entity;
    }

    private WebUiEnvironmentItem toEnvironmentItem(WebUiEnvironmentEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new WebUiEnvironmentItem(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getEnvironmentName(),
                entity.getBaseUrl(),
                entity.getBrowserType(),
                entity.getHeadless(),
                entity.getDefaultTimeoutMs(),
                entity.getStatus(),
                "WEB_UI",
                null,
                null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WebUiEnvironmentItem toPublicEnvironmentItem(EnvConfigEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        WebUiExecutionContextSupport.WebUiEnvironmentConfig config =
                executionContextSupport.readEnvironmentConfig(entity.getConfigJson());
        ParamSetEntity defaultVariableSet = config.defaultVariableSetId() == null
                ? null
                : paramSetMapper.selectById(config.defaultVariableSetId());
        return new WebUiEnvironmentItem(
                -entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getEnvName(),
                entity.getBaseUrl(),
                normalizeBrowserType(config.browserType()),
                config.headless() == null || config.headless(),
                normalizeCaseTimeout(config.defaultTimeoutMs()),
                entity.getStatus(),
                "CONFIG_CENTER",
                config.defaultVariableSetId(),
                defaultVariableSet == null ? null : defaultVariableSet.getParamName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
