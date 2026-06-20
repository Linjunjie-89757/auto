package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.apiautomation.ApiWorkspaceScopeSupport;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.blankToNull;
import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.*;

@Service
public class WebUiCiTokenDomainService {

    private final WebUiCiTokenMapper ciTokenMapper;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebUiCiTokenDomainService(
            WebUiCiTokenMapper ciTokenMapper,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport
    ) {
        this.ciTokenMapper = ciTokenMapper;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
    }

    public PageResponse<WebUiCiTokenSummary> listTokens(String workspaceCode, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<WebUiCiTokenEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, WebUiCiTokenEntity::getWorkspaceId, workspaceCode);
        List<WebUiCiTokenSummary> items = ciTokenMapper.selectList(query
                        .orderByDesc(WebUiCiTokenEntity::getCreatedAt)
                        .orderByDesc(WebUiCiTokenEntity::getId))
                .stream()
                .map(this::toSummary)
                .toList();
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? (items.isEmpty() ? 10 : items.size()) : pageSize;
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PageResponse.of(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    @Transactional
    public WebUiCiTokenCreated createToken(String headerWorkspaceCode, SaveWebUiCiTokenRequest request) {
        if (request == null || blankToNull(request.tokenName()) == null) {
            throw new BadRequestException("Web UI CI token name cannot be blank");
        }
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        String rawToken = generateRawToken();
        LocalDateTime now = LocalDateTime.now();
        WebUiCiTokenEntity entity = new WebUiCiTokenEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setTokenName(request.tokenName().trim());
        entity.setTokenHash(sha256(rawToken));
        entity.setStatus(1);
        entity.setCreatedBy(CurrentUserContext.require().displayName());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        ciTokenMapper.insert(entity);
        return toCreated(entity, rawToken);
    }

    @Transactional
    public WebUiCiTokenSummary disableToken(Long id, String workspaceCode) {
        WebUiCiTokenEntity entity = requireWritableToken(id, workspaceCode);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(0);
        entity.setUpdatedAt(now);
        ciTokenMapper.updateById(entity);
        return toSummary(entity);
    }

    @Transactional
    public WebUiCiTokenCreated rotateToken(Long id, String workspaceCode) {
        WebUiCiTokenEntity entity = requireWritableToken(id, workspaceCode);
        String rawToken = generateRawToken();
        LocalDateTime now = LocalDateTime.now();
        entity.setTokenHash(sha256(rawToken));
        entity.setStatus(1);
        entity.setUpdatedAt(now);
        ciTokenMapper.updateById(entity);
        return toCreated(entity, rawToken);
    }

    @Transactional
    public void deleteToken(Long id, String workspaceCode) {
        WebUiCiTokenEntity entity = requireWritableToken(id, workspaceCode);
        ciTokenMapper.deleteById(entity.getId());
    }

    private WebUiCiTokenEntity requireWritableToken(Long id, String workspaceCode) {
        WebUiCiTokenEntity entity = ciTokenMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Web UI CI token not found");
        }
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        workspaceService.requireWritableWorkspace(workspace.getWorkspaceCode());
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the web UI CI token");
        return entity;
    }

    private WebUiCiTokenSummary toSummary(WebUiCiTokenEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new WebUiCiTokenSummary(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getTokenName(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WebUiCiTokenCreated toCreated(WebUiCiTokenEntity entity, String rawToken) {
        WebUiCiTokenSummary summary = toSummary(entity);
        return new WebUiCiTokenCreated(
                summary.id(),
                summary.workspaceCode(),
                summary.workspaceName(),
                summary.tokenName(),
                summary.status(),
                summary.createdBy(),
                summary.lastUsedAt(),
                summary.createdAt(),
                summary.updatedAt(),
                rawToken
        );
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "webui_ci_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
