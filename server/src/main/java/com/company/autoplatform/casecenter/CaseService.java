package com.company.autoplatform.casecenter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserService;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CaseService {
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Pattern CASE_NO_PATTERN = Pattern.compile("^CASE-(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final CaseDomainService caseDomainService;
    private final CaseDirectoryDomainService caseDirectoryDomainService;
    private final CaseBatchDomainService caseBatchDomainService;
    private final CaseMapper caseMapper;
    private final CaseDirectoryMapper caseDirectoryMapper;
    private final CaseExecutionAttachmentMapper caseExecutionAttachmentMapper;
    private final CaseExecutionAttachmentStorageService caseExecutionAttachmentStorageService;
    private final UserService userService;
    private final WorkspaceService workspaceService;

    public CaseService(
            CaseDomainService caseDomainService,
            CaseDirectoryDomainService caseDirectoryDomainService,
            CaseBatchDomainService caseBatchDomainService,
            CaseMapper caseMapper,
            CaseDirectoryMapper caseDirectoryMapper,
            CaseExecutionAttachmentMapper caseExecutionAttachmentMapper,
            CaseExecutionAttachmentStorageService caseExecutionAttachmentStorageService,
            UserService userService,
            WorkspaceService workspaceService
    ) {
        this.caseDomainService = caseDomainService;
        this.caseDirectoryDomainService = caseDirectoryDomainService;
        this.caseBatchDomainService = caseBatchDomainService;
        this.caseMapper = caseMapper;
        this.caseDirectoryMapper = caseDirectoryMapper;
        this.caseExecutionAttachmentMapper = caseExecutionAttachmentMapper;
        this.caseExecutionAttachmentStorageService = caseExecutionAttachmentStorageService;
        this.userService = userService;
        this.workspaceService = workspaceService;
    }

    public PageResponse<CaseSummaryResponse> listCases(
            String workspaceCode,
            Integer pageNo,
            Integer pageSize,
            Long directoryId,
            String keyword,
            String priority,
            String reviewStatus,
            String executionStatus
    ) {
        return caseDomainService.listCases(
                workspaceCode,
                pageNo,
                pageSize,
                directoryId,
                keyword,
                priority,
                reviewStatus,
                executionStatus);
    }

    public CaseDetailResponse getCase(Long id, String workspaceCode) {
        return caseDomainService.getCase(id, workspaceCode);
    }

    public CaseSummaryResponse createCase(String headerWorkspaceCode, CreateCaseRequest request) {
        return caseDomainService.createCase(headerWorkspaceCode, request);
    }

    public CaseSummaryResponse updateCase(Long id, String headerWorkspaceCode, CreateCaseRequest request) {
        return caseDomainService.updateCase(id, headerWorkspaceCode, request);
    }

    public CaseDetailResponse reviewCase(Long id, String workspaceCode, ReviewCaseRequest request) {
        CaseEntity entity = requireCase(id);
        validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        entity.setReviewStatus(normalizeReviewStatus(request.reviewStatus()));
        entity.setReviewComment(blankToNull(request.reviewComment()));
        entity.setReviewedBy(CurrentUserContext.require().userId());
        entity.setReviewedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(CurrentUserContext.get());
        caseMapper.updateById(entity);
        return toCaseDetail(entity);
    }

    public CaseDetailResponse executeCase(Long id, String workspaceCode, ExecuteCaseRequest request) {
        CaseEntity entity = requireCase(id);
        validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        entity.setExecutionStatus(normalizeExecutionStatus(request.executionStatus()));
        entity.setExecutionComment(blankToNull(request.executionComment()));
        entity.setExecutionNote(blankToNull(request.executionNote()));
        entity.setExecutorId(CurrentUserContext.require().userId());
        entity.setExecutedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(CurrentUserContext.get());
        caseMapper.updateById(entity);
        return toCaseDetail(entity);
    }

    public List<CaseExecutionAttachmentResponse> uploadExecutionAttachments(Long caseId, String workspaceCode, List<MultipartFile> files) {
        CaseEntity entity = requireCase(caseId);
        validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        List<StoredCaseExecutionFile> storedFiles = caseExecutionAttachmentStorageService.storeAll(entity.getWorkspaceId(), caseId, files);
        List<CaseExecutionAttachmentEntity> createdAttachments = new ArrayList<>();
        try {
            for (int i = 0; i < storedFiles.size(); i++) {
                MultipartFile file = files.get(i);
                StoredCaseExecutionFile storedFile = storedFiles.get(i);
                CaseExecutionAttachmentEntity attachment = new CaseExecutionAttachmentEntity();
                attachment.setCaseId(caseId);
                attachment.setWorkspaceId(entity.getWorkspaceId());
                attachment.setFileName(file.getOriginalFilename());
                attachment.setStoredPath(storedFile.storedPath());
                attachment.setContentType(storedFile.contentType());
                attachment.setFileSize(storedFile.fileSize());
                attachment.setCreatedAt(LocalDateTime.now());
                attachment.setUpdatedAt(LocalDateTime.now());
                caseExecutionAttachmentMapper.insert(attachment);
                createdAttachments.add(attachment);
            }
        } catch (RuntimeException exception) {
            for (CaseExecutionAttachmentEntity attachment : createdAttachments) {
                if (attachment.getId() != null) {
                    caseExecutionAttachmentMapper.deleteById(attachment.getId());
                }
                caseExecutionAttachmentStorageService.delete(attachment.getStoredPath());
            }
            for (StoredCaseExecutionFile storedFile : storedFiles) {
                caseExecutionAttachmentStorageService.delete(storedFile.storedPath());
            }
            throw exception;
        }

        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(CurrentUserContext.get());
        caseMapper.updateById(entity);
        return createdAttachments.stream().map(attachment -> toAttachmentResponse(entity, attachment)).toList();
    }

    public void deleteExecutionAttachment(Long caseId, Long attachmentId, String workspaceCode) {
        CaseEntity entity = requireCase(caseId);
        validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        CaseExecutionAttachmentEntity attachment = requireExecutionAttachment(attachmentId);
        if (!attachment.getCaseId().equals(caseId)) {
            throw new BadRequestException("执行附件不属于当前用例");
        }
        caseExecutionAttachmentMapper.deleteById(attachmentId);
        caseExecutionAttachmentStorageService.delete(attachment.getStoredPath());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(CurrentUserContext.get());
        caseMapper.updateById(entity);
    }

    public CaseExecutionFileDownload downloadExecutionAttachment(Long caseId, Long attachmentId, String workspaceCode) {
        CaseEntity entity = requireCase(caseId);
        validateReadable(entity, workspaceCode);
        CaseExecutionAttachmentEntity attachment = requireExecutionAttachment(attachmentId);
        if (!attachment.getCaseId().equals(caseId)) {
            throw new BadRequestException("执行附件不属于当前用例");
        }
        return caseExecutionAttachmentStorageService.load(attachment);
    }

    public PageResponse<CaseSummaryResponse> batchMoveCases(String workspaceCode, BatchMoveCasesRequest request) {
        return caseBatchDomainService.batchMoveCases(workspaceCode, request);
    }

    public PageResponse<CaseSummaryResponse> batchUpdateCases(String workspaceCode, BatchUpdateCasesRequest request) {
        return caseBatchDomainService.batchUpdateCases(workspaceCode, request);
    }

    public void batchDeleteCases(String workspaceCode, BatchDeleteCasesRequest request) {
        caseBatchDomainService.batchDeleteCases(workspaceCode, request);
    }

    public void deleteCase(Long id, String workspaceCode) {
        caseDomainService.deleteCase(id, workspaceCode);
    }

    public List<CaseDirectoryWorkspaceResponse> listDirectories(String workspaceCode) {
        return caseDirectoryDomainService.listDirectories(workspaceCode);
    }

    public CaseDirectoryNodeResponse createDirectory(String headerWorkspaceCode, CreateCaseDirectoryRequest request) {
        return caseDirectoryDomainService.createDirectory(headerWorkspaceCode, request);
    }

    public CaseDirectoryNodeResponse renameDirectory(Long id, String workspaceCode, RenameCaseDirectoryRequest request) {
        return caseDirectoryDomainService.renameDirectory(id, workspaceCode, request);
    }

    public CaseDirectoryNodeResponse moveDirectory(Long id, String workspaceCode, MoveCaseDirectoryRequest request) {
        return caseDirectoryDomainService.moveDirectory(id, workspaceCode, request);
    }

    public void deleteDirectory(Long id, String workspaceCode) {
        caseDirectoryDomainService.deleteDirectory(id, workspaceCode);
    }

    public CaseEntity requireCase(Long id) {
        return caseDomainService.requireCase(id);
    }

    private CaseExecutionAttachmentEntity requireExecutionAttachment(Long attachmentId) {
        CaseExecutionAttachmentEntity attachment = caseExecutionAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new NotFoundException("执行附件不存在");
        }
        return attachment;
    }

    public CaseDirectoryEntity requireDirectory(Long id) {
        return caseDirectoryDomainService.requireDirectory(id);
    }

    private void validateReadable(CaseEntity entity, String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            if (!workspaceService.isPlatformAdmin()
                    && !workspaceService.listReadableWorkspaceIds().contains(entity.getWorkspaceId())) {
                throw new BadRequestException("当前空间上下文不可访问该用例");
            }
            return;
        }
        WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(normalized);
        if (!workspace.getId().equals(entity.getWorkspaceId())) {
            throw new BadRequestException("当前空间上下文不可访问该用例");
        }
    }

    private void validateDirectoryReadable(CaseDirectoryEntity entity, String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            if (!workspaceService.isPlatformAdmin()
                    && !workspaceService.listReadableWorkspaceIds().contains(entity.getWorkspaceId())) {
                throw new BadRequestException("当前空间上下文不可访问该目录");
            }
            return;
        }
        WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(normalized);
        if (!workspace.getId().equals(entity.getWorkspaceId())) {
            throw new BadRequestException("当前空间上下文不可访问该目录");
        }
    }

    private CaseDirectoryEntity requireDirectoryForWorkspace(WorkspaceEntity workspace, Long directoryId) {
        if (directoryId == null) {
            return null;
        }
        CaseDirectoryEntity directory = requireDirectory(directoryId);
        if (!directory.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("目录不属于当前工作空间");
        }
        return directory;
    }

    private CaseDirectoryEntity requireParentDirectory(WorkspaceEntity workspace, Long parentId) {
        if (parentId == null) {
            return null;
        }
        return requireDirectoryForWorkspace(workspace, parentId);
    }

    private PageResponse<CaseSummaryResponse> toSummaryPage(List<CaseEntity> entities) {
        Map<Long, UserEntity> userMap = collectUserMap(entities);
        Map<Long, WorkspaceEntity> workspaceMap = collectWorkspaceMap(entities);
        Map<Long, CaseDirectoryEntity> directoryMap = collectDirectoryMap(entities);
        List<CaseSummaryResponse> items = entities.stream()
                .map(item -> toCaseSummary(item, userMap, workspaceMap, directoryMap))
                .toList();
        return PageResponse.of(items, items.size(), 1, items.size());
    }

    private CaseSummaryResponse getCaseSummary(Long id) {
        CaseEntity item = requireCase(id);
        Map<Long, UserEntity> userMap = collectUserMap(List.of(item));
        Map<Long, WorkspaceEntity> workspaceMap = collectWorkspaceMap(List.of(item));
        Map<Long, CaseDirectoryEntity> directoryMap = collectDirectoryMap(List.of(item));
        return toCaseSummary(item, userMap, workspaceMap, directoryMap);
    }

    private CaseSummaryResponse toCaseSummary(
            CaseEntity item,
            Map<Long, UserEntity> userMap,
            Map<Long, WorkspaceEntity> workspaceMap,
            Map<Long, CaseDirectoryEntity> directoryMap
    ) {
        UserEntity owner = item.getOwnerId() == null ? null : userMap.get(item.getOwnerId());
        WorkspaceEntity workspace = workspaceMap.get(item.getWorkspaceId());
        CaseDirectoryEntity directory = item.getCaseDirectoryId() == null ? null : directoryMap.get(item.getCaseDirectoryId());
        UserEntity reviewer = item.getReviewedBy() == null ? null : userMap.get(item.getReviewedBy());
        UserEntity executor = item.getExecutorId() == null ? null : userMap.get(item.getExecutorId());
        UserEntity creator = item.getCreatedBy() == null ? null : userMap.get(item.getCreatedBy());
        UserEntity updater = item.getUpdatedBy() == null ? null : userMap.get(item.getUpdatedBy());
        return new CaseSummaryResponse(
                item.getId(),
                item.getCaseNo(),
                item.getTitle(),
                item.getCaseType(),
                item.getPriority(),
                item.getSourceType(),
                item.getCaseStatus(),
                defaultExecutionStatus(item.getExecutionStatus()),
                owner == null ? "-" : owner.getDisplayName(),
                executor == null ? "-" : executor.getDisplayName(),
                item.getExecutionComment(),
                item.getExecutedAt() == null ? null : item.getExecutedAt().toString(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                directory == null ? null : directory.getId(),
                directory == null ? null : directory.getDirectoryName(),
                item.getCreatedBy(),
                creator == null ? null : creator.getDisplayName(),
                item.getCreatedAt() == null ? null : item.getCreatedAt().toString(),
                item.getUpdatedBy(),
                updater == null ? null : updater.getDisplayName(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString(),
                defaultReviewStatus(item.getReviewStatus()),
                item.getReviewComment(),
                item.getReviewedBy(),
                reviewer == null ? null : reviewer.getDisplayName(),
                item.getReviewedAt() == null ? null : item.getReviewedAt().toString()
        );
    }

    private CaseDetailResponse toCaseDetail(CaseEntity item) {
        UserEntity owner = item.getOwnerId() == null ? null : userService.requireUser(item.getOwnerId());
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        CaseDirectoryEntity directory = item.getCaseDirectoryId() == null ? null : requireDirectory(item.getCaseDirectoryId());
        UserEntity reviewer = item.getReviewedBy() == null ? null : userService.requireUser(item.getReviewedBy());
        UserEntity executor = item.getExecutorId() == null ? null : userService.requireUser(item.getExecutorId());
        UserEntity creator = item.getCreatedBy() == null ? null : userService.requireUser(item.getCreatedBy());
        UserEntity updater = item.getUpdatedBy() == null ? null : userService.requireUser(item.getUpdatedBy());
        List<CaseExecutionAttachmentResponse> attachments = caseExecutionAttachmentMapper.selectList(new LambdaQueryWrapper<CaseExecutionAttachmentEntity>()
                        .eq(CaseExecutionAttachmentEntity::getCaseId, item.getId())
                        .orderByAsc(CaseExecutionAttachmentEntity::getId))
                .stream()
                .map(attachment -> toAttachmentResponse(item, attachment))
                .toList();
        return new CaseDetailResponse(
                item.getId(),
                item.getCaseNo(),
                item.getTitle(),
                item.getCaseType(),
                item.getPriority(),
                item.getSourceType(),
                item.getCaseStatus(),
                item.getOwnerId(),
                owner == null ? "-" : owner.getDisplayName(),
                defaultExecutionStatus(item.getExecutionStatus()),
                item.getExecutorId(),
                executor == null ? "-" : executor.getDisplayName(),
                item.getExecutionComment(),
                item.getExecutionNote(),
                item.getExecutedAt() == null ? null : item.getExecutedAt().toString(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                directory == null ? null : directory.getId(),
                directory == null ? null : directory.getDirectoryName(),
                item.getCreatedBy(),
                creator == null ? null : creator.getDisplayName(),
                item.getCreatedAt() == null ? null : item.getCreatedAt().toString(),
                item.getUpdatedBy(),
                updater == null ? null : updater.getDisplayName(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString(),
                defaultReviewStatus(item.getReviewStatus()),
                item.getReviewComment(),
                item.getReviewedBy(),
                reviewer == null ? null : reviewer.getDisplayName(),
                item.getReviewedAt() == null ? null : item.getReviewedAt().toString(),
                item.getPrecondition(),
                item.getSteps(),
                item.getExpectedResult(),
                attachments
        );
    }

    private CaseExecutionAttachmentResponse toAttachmentResponse(CaseEntity item, CaseExecutionAttachmentEntity attachment) {
        return new CaseExecutionAttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                "/api/cases/" + item.getId() + "/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }

    private Map<Long, UserEntity> collectUserMap(List<CaseEntity> entities) {
        List<Long> userIds = entities.stream()
                .flatMap(item -> java.util.stream.Stream.of(
                        item.getOwnerId(),
                        item.getExecutorId(),
                        item.getCreatedBy(),
                        item.getUpdatedBy(),
                        item.getReviewedBy()
                ))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userIds.stream()
                .map(userService::requireUser)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private Map<Long, WorkspaceEntity> collectWorkspaceMap(List<CaseEntity> entities) {
        List<Long> workspaceIds = entities.stream()
                .map(CaseEntity::getWorkspaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return workspaceIds.stream()
                .map(workspaceService::requireWorkspaceById)
                .collect(Collectors.toMap(WorkspaceEntity::getId, Function.identity()));
    }

    private Map<Long, CaseDirectoryEntity> collectDirectoryMap(List<CaseEntity> entities) {
        List<Long> directoryIds = entities.stream()
                .map(CaseEntity::getCaseDirectoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (directoryIds.isEmpty()) {
            return Map.of();
        }
        return caseDirectoryMapper.selectList(new LambdaQueryWrapper<CaseDirectoryEntity>()
                        .in(CaseDirectoryEntity::getId, directoryIds))
                .stream()
                .collect(Collectors.toMap(CaseDirectoryEntity::getId, Function.identity()));
    }

    private List<CaseDirectoryNodeResponse> buildDirectoryTree(WorkspaceEntity workspace, List<CaseDirectoryEntity> directories) {
        Map<Long, List<CaseDirectoryEntity>> childrenByParent = directories.stream()
                .collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId(), LinkedHashMap::new, Collectors.toList()));
        return buildDirectoryChildren(workspace, childrenByParent, 0L);
    }

    private List<CaseDirectoryNodeResponse> buildDirectoryChildren(
            WorkspaceEntity workspace,
            Map<Long, List<CaseDirectoryEntity>> childrenByParent,
            Long parentId
    ) {
        List<CaseDirectoryEntity> currentChildren = childrenByParent.getOrDefault(parentId, List.of());
        List<CaseDirectoryNodeResponse> result = new ArrayList<>();
        for (CaseDirectoryEntity child : currentChildren) {
            result.add(toDirectoryNode(child, workspace, buildDirectoryChildren(workspace, childrenByParent, child.getId())));
        }
        return result;
    }

    private CaseDirectoryNodeResponse toDirectoryNode(
            CaseDirectoryEntity entity,
            WorkspaceEntity workspace,
            List<CaseDirectoryNodeResponse> children
    ) {
        return new CaseDirectoryNodeResponse(
                entity.getId(),
                entity.getDirectoryName(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getParentId(),
                children
        );
    }

    private Set<Long> collectDescendantIds(Long workspaceId, Long rootId) {
        List<CaseDirectoryEntity> directories = caseDirectoryMapper.selectList(new LambdaQueryWrapper<CaseDirectoryEntity>()
                .eq(CaseDirectoryEntity::getWorkspaceId, workspaceId)
                .orderByAsc(CaseDirectoryEntity::getId));
        Map<Long, List<CaseDirectoryEntity>> childrenByParent = directories.stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.groupingBy(CaseDirectoryEntity::getParentId));

        Set<Long> result = new HashSet<>();
        List<Long> stack = new ArrayList<>();
        stack.add(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.remove(stack.size() - 1);
            result.add(current);
            for (CaseDirectoryEntity child : childrenByParent.getOrDefault(current, List.of())) {
                stack.add(child.getId());
            }
        }
        return result;
    }

    private List<CaseEntity> requireWritableCases(List<Long> caseIds, String workspaceCode) {
        List<Long> distinctIds = caseIds == null ? List.of() : caseIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BadRequestException("用例列表不能为空");
        }
        List<CaseEntity> entities = distinctIds.stream().map(this::requireCase).toList();
        for (CaseEntity entity : entities) {
            validateReadable(entity, workspaceCode);
            workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        }
        return entities;
    }

    private Long assertSingleWorkspace(List<CaseEntity> entities) {
        Set<Long> workspaceIds = entities.stream().map(CaseEntity::getWorkspaceId).collect(Collectors.toSet());
        if (workspaceIds.size() != 1) {
            throw new BadRequestException("批量操作暂不支持跨空间混合提交");
        }
        return entities.getFirst().getWorkspaceId();
    }

    private String normalizeReviewStatus(String reviewStatus) {
        String normalized = reviewStatus == null ? "" : reviewStatus.trim().toUpperCase();
        return switch (normalized) {
            case "PENDING", "PASSED", "REJECTED" -> normalized;
            default -> throw new BadRequestException("评审状态不合法");
        };
    }

    private String defaultReviewStatus(String reviewStatus) {
        return blankToNull(reviewStatus) == null ? "PENDING" : reviewStatus;
    }

    private String defaultExecutionStatus(String executionStatus) {
        String normalized = blankToNull(executionStatus);
        if (normalized == null) {
            return "NOT_RUN";
        }
        if ("RUNNING".equalsIgnoreCase(normalized)) {
            return "BLOCKED";
        }
        return normalized.trim().toUpperCase();
    }

    private String normalizeExecutionStatus(String executionStatus) {
        String normalized = executionStatus == null ? "" : executionStatus.trim().toUpperCase();
        if ("RUNNING".equals(normalized)) {
            normalized = "BLOCKED";
        }
        return switch (normalized) {
            case "NOT_RUN", "PASSED", "BLOCKED", "FAILED" -> normalized;
            default -> throw new BadRequestException("执行状态不合法");
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String generateCaseNo() {
        List<CaseEntity> latestCases = caseMapper.selectList(new LambdaQueryWrapper<CaseEntity>()
                .select(CaseEntity::getCaseNo)
                .orderByDesc(CaseEntity::getId)
                .last("limit 1"));

        long nextSequence = 1;
        if (!latestCases.isEmpty()) {
            String latestCaseNo = latestCases.getFirst().getCaseNo();
            Matcher matcher = CASE_NO_PATTERN.matcher(latestCaseNo == null ? "" : latestCaseNo.trim());
            if (matcher.matches()) {
                nextSequence = Long.parseLong(matcher.group(1)) + 1;
            } else {
                nextSequence = caseMapper.selectCount(new LambdaQueryWrapper<>()) + 1;
            }
        }
        return "Case-" + String.format("%05d", nextSequence);
    }
}
