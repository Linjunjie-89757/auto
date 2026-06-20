package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.apiautomation.ApiWorkspaceScopeSupport;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
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
import java.util.Locale;

import static com.company.autoplatform.webuiautomation.WebUiAutomationFormatSupport.blankToNull;
import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.*;

@Service
public class WebUiReportShareDomainService {

    private static final String RUN = "RUN";
    private static final String BATCH = "BATCH";

    private final WebUiReportShareMapper reportShareMapper;
    private final WebUiExecutionDomainService executionDomainService;
    private final WorkspaceService workspaceService;
    private final ApiWorkspaceScopeSupport workspaceScopeSupport;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebUiReportShareDomainService(
            WebUiReportShareMapper reportShareMapper,
            WebUiExecutionDomainService executionDomainService,
            WorkspaceService workspaceService,
            ApiWorkspaceScopeSupport workspaceScopeSupport
    ) {
        this.reportShareMapper = reportShareMapper;
        this.executionDomainService = executionDomainService;
        this.workspaceService = workspaceService;
        this.workspaceScopeSupport = workspaceScopeSupport;
    }

    @Transactional
    public WebUiReportShareCreated createShare(String workspaceCode, SaveWebUiReportShareRequest request) {
        if (request == null) {
            throw new BadRequestException("Web UI report share request cannot be empty");
        }
        String shareType = normalizeShareType(request.shareType());
        if (request.targetId() == null) {
            throw new BadRequestException("Web UI report share target cannot be empty");
        }
        Long workspaceId = requireTargetWorkspaceId(shareType, request.targetId(), workspaceCode);
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(workspaceId);
        workspaceService.requireWritableWorkspace(workspace.getWorkspaceCode());
        String rawToken = generateRawToken();
        LocalDateTime now = LocalDateTime.now();
        WebUiReportShareEntity entity = new WebUiReportShareEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setShareType(shareType);
        entity.setTargetId(request.targetId());
        entity.setTokenHash(sha256(rawToken));
        entity.setStatus(1);
        entity.setExpiresAt(resolveExpiresAt(request.expiresInDays(), now));
        entity.setCreatedBy(CurrentUserContext.require().displayName());
        entity.setAccessCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        reportShareMapper.insert(entity);
        return toCreated(entity, rawToken);
    }

    public List<WebUiReportShareSummary> listShares(String workspaceCode, String shareType, Long targetId) {
        String normalizedShareType = normalizeShareType(shareType);
        if (targetId == null) {
            throw new BadRequestException("Web UI report share target cannot be empty");
        }
        LambdaQueryWrapper<WebUiReportShareEntity> query = new LambdaQueryWrapper<>();
        workspaceScopeSupport.applyWorkspaceScope(query, WebUiReportShareEntity::getWorkspaceId, workspaceCode);
        query.eq(WebUiReportShareEntity::getShareType, normalizedShareType)
                .eq(WebUiReportShareEntity::getTargetId, targetId)
                .orderByDesc(WebUiReportShareEntity::getCreatedAt)
                .orderByDesc(WebUiReportShareEntity::getId);
        return reportShareMapper.selectList(query).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public WebUiReportShareSummary revokeShare(Long id, String workspaceCode) {
        WebUiReportShareEntity entity = requireWritableShare(id, workspaceCode);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(0);
        entity.setUpdatedAt(now);
        reportShareMapper.updateById(entity);
        return toSummary(entity);
    }

    @Transactional
    public WebUiReportShareCreated regenerateShare(Long id, String workspaceCode) {
        WebUiReportShareEntity entity = requireWritableShare(id, workspaceCode);
        String rawToken = generateRawToken();
        LocalDateTime now = LocalDateTime.now();
        entity.setTokenHash(sha256(rawToken));
        entity.setStatus(1);
        entity.setAccessCount(0);
        entity.setLastAccessedAt(null);
        entity.setUpdatedAt(now);
        reportShareMapper.updateById(entity);
        return toCreated(entity, rawToken);
    }

    @Transactional
    public WebUiSharedReport getSharedReport(String rawToken) {
        WebUiReportShareEntity share = requireActiveShare(rawToken);
        LocalDateTime now = LocalDateTime.now();
        share.setLastAccessedAt(now);
        share.setAccessCount((share.getAccessCount() == null ? 0 : share.getAccessCount()) + 1);
        share.setUpdatedAt(now);
        reportShareMapper.updateById(share);
        if (RUN.equals(share.getShareType())) {
            return new WebUiSharedReport(
                    RUN,
                    executionDomainService.getSharedRun(share.getTargetId(), share.getWorkspaceId(), rawToken),
                    null,
                    share.getExpiresAt(),
                    now
            );
        }
        return new WebUiSharedReport(
                BATCH,
                null,
                executionDomainService.getSharedBatch(share.getTargetId(), share.getWorkspaceId()),
                share.getExpiresAt(),
                now
        );
    }

    public WebUiArtifactFileDownload downloadSharedArtifact(String rawToken, Long runId, Long artifactId) {
        WebUiReportShareEntity share = requireActiveShare(rawToken);
        if (RUN.equals(share.getShareType()) && !share.getTargetId().equals(runId)) {
            throw new NotFoundException("Web UI shared artifact not found");
        }
        if (BATCH.equals(share.getShareType())) {
            WebUiRunDetail run = executionDomainService.getSharedRun(runId, share.getWorkspaceId(), rawToken);
            if (run.summary().batchId() == null || !run.summary().batchId().equals(share.getTargetId())) {
                throw new NotFoundException("Web UI shared artifact not found");
            }
        }
        return executionDomainService.downloadSharedArtifact(runId, artifactId, share.getWorkspaceId());
    }

    private Long requireTargetWorkspaceId(String shareType, Long targetId, String workspaceCode) {
        if (RUN.equals(shareType)) {
            WebUiRunDetail run = executionDomainService.getRun(targetId, workspaceCode);
            return workspaceService.requireWorkspace(run.summary().workspaceCode()).getId();
        }
        WebUiRunBatchDetail batch = executionDomainService.getBatch(targetId, workspaceCode);
        return workspaceService.requireWorkspace(batch.summary().workspaceCode()).getId();
    }

    private WebUiReportShareEntity requireWritableShare(Long id, String workspaceCode) {
        WebUiReportShareEntity entity = reportShareMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Web UI report share not found");
        }
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        workspaceService.requireWritableWorkspace(workspace.getWorkspaceCode());
        workspaceScopeSupport.validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot access the web UI report share");
        return entity;
    }

    private WebUiReportShareEntity requireActiveShare(String rawToken) {
        String token = blankToNull(rawToken);
        if (token == null) {
            throw new NotFoundException("Web UI report share not found");
        }
        WebUiReportShareEntity entity = reportShareMapper.selectOne(new LambdaQueryWrapper<WebUiReportShareEntity>()
                .eq(WebUiReportShareEntity::getTokenHash, sha256(token))
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (entity == null || entity.getStatus() == null || entity.getStatus() != 1
                || (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now))) {
            throw new NotFoundException("Web UI report share not found");
        }
        return entity;
    }

    private String normalizeShareType(String shareType) {
        String normalized = blankToNull(shareType);
        if (normalized == null) {
            throw new BadRequestException("Web UI report share type cannot be blank");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!RUN.equals(normalized) && !BATCH.equals(normalized)) {
            throw new BadRequestException("Web UI report share type must be RUN or BATCH");
        }
        return normalized;
    }

    private LocalDateTime resolveExpiresAt(Integer expiresInDays, LocalDateTime now) {
        if (expiresInDays == null) {
            return now.plusDays(7);
        }
        if (expiresInDays <= 0) {
            return null;
        }
        return now.plusDays(Math.min(expiresInDays, 365));
    }

    private WebUiReportShareSummary toSummary(WebUiReportShareEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        return new WebUiReportShareSummary(
                entity.getId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getShareType(),
                entity.getTargetId(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getCreatedBy(),
                entity.getLastAccessedAt(),
                entity.getAccessCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private WebUiReportShareCreated toCreated(WebUiReportShareEntity entity, String rawToken) {
        WebUiReportShareSummary summary = toSummary(entity);
        return new WebUiReportShareCreated(
                summary.id(),
                summary.workspaceCode(),
                summary.workspaceName(),
                summary.shareType(),
                summary.targetId(),
                summary.status(),
                summary.expiresAt(),
                summary.createdBy(),
                summary.lastAccessedAt(),
                summary.accessCount(),
                summary.createdAt(),
                summary.updatedAt(),
                rawToken,
                "/share/web-ui/report?token=" + rawToken
        );
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "webui_share_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
