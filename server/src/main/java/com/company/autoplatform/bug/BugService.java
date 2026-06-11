package com.company.autoplatform.bug;

import com.company.autoplatform.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class BugService {
    private final BugDomainService bugDomainService;
    private final BugAttachmentSupport bugAttachmentSupport;
    private final BugWorkflowDomainService bugWorkflowDomainService;
    private final BugCommentDomainService bugCommentDomainService;
    private final BugSourceContextSupport bugSourceContextSupport;
    private final BugResponseAssembler bugResponseAssembler;

    public BugService(BugDomainService bugDomainService, BugAttachmentSupport bugAttachmentSupport,
                      BugWorkflowDomainService bugWorkflowDomainService,
                      BugCommentDomainService bugCommentDomainService,
                      BugSourceContextSupport bugSourceContextSupport,
                      BugResponseAssembler bugResponseAssembler) {
        this.bugDomainService = bugDomainService;
        this.bugAttachmentSupport = bugAttachmentSupport;
        this.bugWorkflowDomainService = bugWorkflowDomainService;
        this.bugCommentDomainService = bugCommentDomainService;
        this.bugSourceContextSupport = bugSourceContextSupport;
        this.bugResponseAssembler = bugResponseAssembler;
    }

    public PageResponse<BugSummaryResponse> listBugs(
            String workspaceCode,
            String keyword,
            String status,
            String severity,
            String priority,
            Integer pageNo,
            Integer pageSize
    ) {
        PageResponse<BugEntity> page = bugDomainService.listBugs(workspaceCode, keyword, status, severity, priority, pageNo, pageSize);
        return new PageResponse<>(
                page.items().stream().map(bugResponseAssembler::toSummary).toList(),
                page.total(),
                page.pageNo(),
                page.pageSize(),
                page.totalPages()
        );
    }

    public BugDetailResponse getBug(Long id, String workspaceCode) {
        return bugResponseAssembler.toDetail(bugDomainService.getBug(id, workspaceCode));
    }

    public BugDetailResponse createBug(String headerWorkspaceCode, CreateBugRequest request, BugSourceType sourceType) {
        return bugResponseAssembler.toDetail(bugDomainService.createBug(headerWorkspaceCode, request, sourceType));
    }

    public BugDetailResponse updateBug(Long id, String headerWorkspaceCode, UpdateBugRequest request) {
        return bugResponseAssembler.toDetail(bugDomainService.updateBug(id, headerWorkspaceCode, request));
    }

    public BugDetailResponse assignBug(Long id, String headerWorkspaceCode, AssignBugRequest request) {
        return bugResponseAssembler.toDetail(bugWorkflowDomainService.assignBug(id, headerWorkspaceCode, request));
    }

    public BugDetailResponse transitionBug(Long id, String headerWorkspaceCode, TransitionBugRequest request) {
        return bugResponseAssembler.toDetail(bugWorkflowDomainService.transitionBug(id, headerWorkspaceCode, request));
    }

    public List<BugCommentResponse> listComments(Long id, String workspaceCode) {
        return bugCommentDomainService.listComments(id, workspaceCode).stream().map(bugResponseAssembler::toComment).toList();
    }

    public BugCommentResponse addComment(Long id, String workspaceCode, CreateBugCommentRequest request) {
        return bugResponseAssembler.toComment(bugCommentDomainService.addComment(id, workspaceCode, request));
    }

    public List<BugAttachmentResponse> uploadBugAttachments(Long bugId, String workspaceCode, List<MultipartFile> files) {
        return bugAttachmentSupport.uploadBugAttachments(bugId, workspaceCode, files);
    }

    public void deleteBugAttachment(Long bugId, Long attachmentId, String workspaceCode) {
        bugAttachmentSupport.deleteBugAttachment(bugId, attachmentId, workspaceCode);
    }

    public BugFileDownload downloadBugAttachment(Long bugId, Long attachmentId, String workspaceCode) {
        return bugAttachmentSupport.downloadBugAttachment(bugId, attachmentId, workspaceCode);
    }

    public BugStatisticsResponse statistics(String workspaceCode) {
        return bugDomainService.statistics(workspaceCode);
    }

    public BugDetailResponse createBugFromCase(Long caseId, String workspaceCode, CreateBugRequest request) {
        return createBug(workspaceCode, bugSourceContextSupport.mergeCaseSource(caseId, request), BugSourceType.CASE);
    }

    public BugDetailResponse createBugFromReport(Long reportId, String workspaceCode, CreateBugRequest request) {
        return createBug(workspaceCode, bugSourceContextSupport.mergeReportSource(reportId, request), BugSourceType.REPORT);
    }

    public BugEntity requireBug(Long id) {
        return bugDomainService.requireBug(id);
    }

}
