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

    private final CaseMapper caseMapper;
    private final CaseDirectoryMapper caseDirectoryMapper;
    private final CaseExecutionAttachmentMapper caseExecutionAttachmentMapper;
    private final CaseExecutionAttachmentStorageService caseExecutionAttachmentStorageService;
    private final UserService userService;
    private final WorkspaceService workspaceService;

    public CaseService(
            CaseMapper caseMapper,
            CaseDirectoryMapper caseDirectoryMapper,
            CaseExecutionAttachmentMapper caseExecutionAttachmentMapper,
            CaseExecutionAttachmentStorageService caseExecutionAttachmentStorageService,
            UserService userService,
            WorkspaceService workspaceService
    ) {
        this.caseMapper = caseMapper;
        this.caseDirectoryMapper = caseDirectoryMapper;
        this.caseExecutionAttachmentMapper = caseExecutionAttachmentMapper;
        this.caseExecutionAttachmentStorageService = caseExecutionAttachmentStorageService;
        this.userService = userService;
        this.workspaceService = workspaceService;
    }

    public PageResponse<CaseSummaryResponse> listCases(String workspaceCode, Integer pageNo, Integer pageSize, Long directoryId) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        int safePageNo = pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;

        LambdaQueryWrapper<CaseEntity> query = new LambdaQueryWrapper<>();
        if (!WorkspaceScope.isAll(normalized)) {
            WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(normalized);
            query.eq(CaseEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return PageResponse.of(List.of(), 0, safePageNo, safePageSize);
            }
            query.in(CaseEntity::getWorkspaceId, workspaceIds);
        }

        if (directoryId != null) {
            CaseDirectoryEntity directory = requireDirectory(directoryId);
            validateDirectoryReadable(directory, workspaceCode);
            Set<Long> directoryIds = collectDescendantIds(directory.getWorkspaceId(), directory.getId());
            query.in(CaseEntity::getCaseDirectoryId, directoryIds);
        }

        long total = caseMapper.selectCount(query);
        if (total == 0) {
            return PageResponse.of(List.of(), 0, safePageNo, safePageSize);
        }

        int offset = (safePageNo - 1) * safePageSize;
        List<CaseEntity> entities = caseMapper.selectList(query
                .orderByDesc(CaseEntity::getUpdatedAt)
                .orderByDesc(CaseEntity::getId)
                .last("limit " + safePageSize + " offset " + offset));

        Map<Long, UserEntity> userMap = collectUserMap(entities);
        Map<Long, WorkspaceEntity> workspaceMap = collectWorkspaceMap(entities);
        Map<Long, CaseDirectoryEntity> directoryMap = collectDirectoryMap(entities);

        List<CaseSummaryResponse> items = entities.stream()
                .map(item -> toCaseSummary(item, userMap, workspaceMap, directoryMap))
                .toList();
        return PageResponse.of(items, total, safePageNo, safePageSize);
    }

    public CaseDetailResponse getCase(Long id, String workspaceCode) {
        CaseEntity entity = requireCase(id);
        validateReadable(entity, workspaceCode);
        return toCaseDetail(entity);
    }

    public CaseSummaryResponse createCase(String headerWorkspaceCode, CreateCaseRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (request.ownerId() != null) {
            userService.requireUser(request.ownerId());
        }
        CaseDirectoryEntity directory = requireDirectoryForWorkspace(workspace, request.directoryId());

        CaseEntity entity = new CaseEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setCaseNo(generateCaseNo());
        entity.setTitle(request.title());
        entity.setCaseType(request.caseType());
        entity.setPriority(request.priority());
        entity.setSourceType(request.sourceType());
        entity.setCaseStatus(request.caseStatus());
        entity.setOwnerId(request.ownerId());
        entity.setExecutionStatus("NOT_RUN");
        entity.setExecutorId(null);
        entity.setExecutionComment(null);
        entity.setExecutedAt(null);
        entity.setCaseDirectoryId(directory == null ? null : directory.getId());
        entity.setReviewStatus("PENDING");
        entity.setReviewComment(null);
        entity.setReviewedBy(null);
        entity.setReviewedAt(null);
        entity.setPrecondition(request.precondition());
        entity.setSteps(request.steps());
        entity.setExpectedResult(request.expectedResult());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setCreatedBy(CurrentUserContext.get());
        entity.setUpdatedBy(CurrentUserContext.get());
        caseMapper.insert(entity);
        return getCaseSummary(entity.getId());
    }

    public CaseSummaryResponse updateCase(Long id, String headerWorkspaceCode, CreateCaseRequest request) {
        CaseEntity entity = requireCase(id);
        validateReadable(entity, headerWorkspaceCode);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改用例归属空间");
        }
        if (request.ownerId() != null) {
            userService.requireUser(request.ownerId());
        }
        CaseDirectoryEntity directory = requireDirectoryForWorkspace(workspace, request.directoryId());

        entity.setTitle(request.title());
        entity.setCaseType(request.caseType());
        entity.setPriority(request.priority());
        entity.setSourceType(request.sourceType());
        entity.setCaseStatus(request.caseStatus());
        entity.setOwnerId(request.ownerId());
        entity.setCaseDirectoryId(directory == null ? null : directory.getId());
        entity.setPrecondition(request.precondition());
        entity.setSteps(request.steps());
        entity.setExpectedResult(request.expectedResult());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(CurrentUserContext.get());
        caseMapper.updateById(entity);
        return getCaseSummary(id);
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
        List<CaseEntity> entities = requireWritableCases(request.caseIds(), workspaceCode);
        Long workspaceId = assertSingleWorkspace(entities);
        CaseDirectoryEntity targetDirectory = request.targetDirectoryId() == null ? null : requireDirectory(request.targetDirectoryId());
        if (targetDirectory != null && !targetDirectory.getWorkspaceId().equals(workspaceId)) {
            throw new BadRequestException("批量移动目标目录与用例不在同一空间");
        }

        LocalDateTime now = LocalDateTime.now();
        for (CaseEntity entity : entities) {
            entity.setCaseDirectoryId(targetDirectory == null ? null : targetDirectory.getId());
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(CurrentUserContext.get());
            caseMapper.updateById(entity);
        }
        return toSummaryPage(entities);
    }

    public PageResponse<CaseSummaryResponse> batchUpdateCases(String workspaceCode, BatchUpdateCasesRequest request) {
        if (blankToNull(request.priority()) == null
                && blankToNull(request.reviewStatus()) == null
                && blankToNull(request.executionStatus()) == null) {
            throw new BadRequestException("批量编辑至少选择一个可修改字段");
        }
        List<CaseEntity> entities = requireWritableCases(request.caseIds(), workspaceCode);
        LocalDateTime now = LocalDateTime.now();
        for (CaseEntity entity : entities) {
            if (blankToNull(request.priority()) != null) {
                entity.setPriority(request.priority().trim());
            }
            if (blankToNull(request.reviewStatus()) != null) {
                entity.setReviewStatus(normalizeReviewStatus(request.reviewStatus()));
                entity.setReviewedBy(CurrentUserContext.require().userId());
                entity.setReviewedAt(now);
            }
            if (blankToNull(request.executionStatus()) != null) {
                entity.setExecutionStatus(normalizeExecutionStatus(request.executionStatus()));
                entity.setExecutorId(CurrentUserContext.require().userId());
                entity.setExecutedAt(now);
            }
            entity.setUpdatedAt(now);
            entity.setUpdatedBy(CurrentUserContext.get());
            caseMapper.updateById(entity);
        }
        return toSummaryPage(entities);
    }

    public void batchDeleteCases(String workspaceCode, BatchDeleteCasesRequest request) {
        List<CaseEntity> entities = requireWritableCases(request.caseIds(), workspaceCode);
        for (CaseEntity entity : entities) {
            caseMapper.deleteById(entity.getId());
        }
    }

    public void deleteCase(Long id, String workspaceCode) {
        CaseEntity entity = requireCase(id);
        validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        caseMapper.deleteById(id);
    }

    public List<CaseDirectoryWorkspaceResponse> listDirectories(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        List<WorkspaceEntity> readableWorkspaces;
        if (!WorkspaceScope.isAll(normalized)) {
            readableWorkspaces = List.of(workspaceService.requireReadableWorkspace(normalized));
        } else {
            readableWorkspaces = workspaceService.listReadableWorkspaceEntities();
        }
        if (readableWorkspaces.isEmpty()) {
            return List.of();
        }

        List<Long> workspaceIds = readableWorkspaces.stream().map(WorkspaceEntity::getId).toList();
        List<CaseDirectoryEntity> directories = caseDirectoryMapper.selectList(new LambdaQueryWrapper<CaseDirectoryEntity>()
                .in(CaseDirectoryEntity::getWorkspaceId, workspaceIds)
                .orderByAsc(CaseDirectoryEntity::getWorkspaceId)
                .orderByAsc(CaseDirectoryEntity::getId));
        Map<Long, List<CaseDirectoryEntity>> grouped = directories.stream()
                .collect(Collectors.groupingBy(CaseDirectoryEntity::getWorkspaceId, LinkedHashMap::new, Collectors.toList()));

        return readableWorkspaces.stream()
                .map(workspace -> new CaseDirectoryWorkspaceResponse(
                        workspace.getWorkspaceCode(),
                        workspace.getWorkspaceName(),
                        buildDirectoryTree(workspace, grouped.getOrDefault(workspace.getId(), List.of()))
                ))
                .toList();
    }

    public CaseDirectoryNodeResponse createDirectory(String headerWorkspaceCode, CreateCaseDirectoryRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        CaseDirectoryEntity parent = requireParentDirectory(workspace, request.parentId());

        CaseDirectoryEntity entity = new CaseDirectoryEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setDirectoryName(request.name().trim());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        caseDirectoryMapper.insert(entity);
        return toDirectoryNode(entity, workspace, List.of());
    }

    public CaseDirectoryNodeResponse renameDirectory(Long id, String workspaceCode, RenameCaseDirectoryRequest request) {
        CaseDirectoryEntity entity = requireDirectory(id);
        validateDirectoryReadable(entity, workspaceCode);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        entity.setDirectoryName(request.name().trim());
        entity.setUpdatedAt(LocalDateTime.now());
        caseDirectoryMapper.updateById(entity);
        return toDirectoryNode(entity, workspace, List.of());
    }

    public CaseDirectoryNodeResponse moveDirectory(Long id, String workspaceCode, MoveCaseDirectoryRequest request) {
        CaseDirectoryEntity entity = requireDirectory(id);
        validateDirectoryReadable(entity, workspaceCode);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        CaseDirectoryEntity targetParent = requireParentDirectory(workspace, request.targetParentId());
        if (targetParent != null && targetParent.getId().equals(entity.getId())) {
            throw new BadRequestException("目录不能移动到自己下面");
        }
        if (targetParent != null) {
            Set<Long> descendantIds = collectDescendantIds(entity.getWorkspaceId(), entity.getId());
            if (descendantIds.contains(targetParent.getId())) {
                throw new BadRequestException("目录不能移动到自己的子节点下面");
            }
        }

        entity.setParentId(targetParent == null ? null : targetParent.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        caseDirectoryMapper.updateById(entity);
        return toDirectoryNode(entity, workspace, List.of());
    }

    public void deleteDirectory(Long id, String workspaceCode) {
        CaseDirectoryEntity entity = requireDirectory(id);
        validateDirectoryReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        long childCount = caseDirectoryMapper.selectCount(new LambdaQueryWrapper<CaseDirectoryEntity>()
                .eq(CaseDirectoryEntity::getParentId, entity.getId()));
        if (childCount > 0) {
            throw new BadRequestException("当前目录下还有子模块，暂不允许删除");
        }

        long boundCaseCount = caseMapper.selectCount(new LambdaQueryWrapper<CaseEntity>()
                .eq(CaseEntity::getCaseDirectoryId, entity.getId()));
        if (boundCaseCount > 0) {
            throw new BadRequestException("当前目录下还有用例，暂不允许删除");
        }

        caseDirectoryMapper.deleteById(id);
    }

    public CaseEntity requireCase(Long id) {
        CaseEntity entity = caseMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("关联用例不存在");
        }
        return entity;
    }

    private CaseExecutionAttachmentEntity requireExecutionAttachment(Long attachmentId) {
        CaseExecutionAttachmentEntity attachment = caseExecutionAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new NotFoundException("执行附件不存在");
        }
        return attachment;
    }

    public CaseDirectoryEntity requireDirectory(Long id) {
        CaseDirectoryEntity entity = caseDirectoryMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("目录不存在");
        }
        return entity;
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
