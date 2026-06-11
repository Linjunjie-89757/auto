package com.company.autoplatform.execution;

import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExecutionReportAttachmentSupport {

    private final ExecutionReportDomainService reportDomainService;
    private final ReportMapper reportMapper;
    private final ReportAttachmentMapper reportAttachmentMapper;
    private final WorkspaceService workspaceService;
    private final ReportAttachmentStorageService reportAttachmentStorageService;

    public ExecutionReportAttachmentSupport(
            ExecutionReportDomainService reportDomainService,
            ReportMapper reportMapper,
            ReportAttachmentMapper reportAttachmentMapper,
            WorkspaceService workspaceService,
            ReportAttachmentStorageService reportAttachmentStorageService
    ) {
        this.reportDomainService = reportDomainService;
        this.reportMapper = reportMapper;
        this.reportAttachmentMapper = reportAttachmentMapper;
        this.workspaceService = workspaceService;
        this.reportAttachmentStorageService = reportAttachmentStorageService;
    }

    public List<ReportAttachmentResponse> uploadReportAttachments(Long reportId, String workspaceCode, List<MultipartFile> files) {
        ReportEntity report = reportDomainService.requireReport(reportId);
        reportDomainService.validateReadableReportWorkspace(report, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(report.getWorkspaceId()).getWorkspaceCode());

        List<StoredReportFile> storedFiles = reportAttachmentStorageService.storeAll(report.getWorkspaceId(), reportId, files);
        List<ReportAttachmentEntity> createdAttachments = new ArrayList<>();
        try {
            for (int i = 0; i < storedFiles.size(); i++) {
                MultipartFile file = files.get(i);
                StoredReportFile storedFile = storedFiles.get(i);
                ReportAttachmentEntity attachment = new ReportAttachmentEntity();
                attachment.setReportId(reportId);
                attachment.setWorkspaceId(report.getWorkspaceId());
                attachment.setFileName(file.getOriginalFilename());
                attachment.setStoredPath(storedFile.storedPath());
                attachment.setContentType(storedFile.contentType());
                attachment.setFileSize(storedFile.fileSize());
                attachment.setCreatedAt(LocalDateTime.now());
                attachment.setUpdatedAt(LocalDateTime.now());
                reportAttachmentMapper.insert(attachment);
                createdAttachments.add(attachment);
            }
        } catch (RuntimeException exception) {
            for (ReportAttachmentEntity attachment : createdAttachments) {
                if (attachment.getId() != null) {
                    reportAttachmentMapper.deleteById(attachment.getId());
                }
                reportAttachmentStorageService.delete(attachment.getStoredPath());
            }
            for (StoredReportFile storedFile : storedFiles) {
                reportAttachmentStorageService.delete(storedFile.storedPath());
            }
            throw exception;
        }

        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
        return createdAttachments.stream()
                .map(attachment -> reportDomainService.toAttachmentResponse(report, attachment))
                .toList();
    }

    public void deleteReportAttachment(Long reportId, Long attachmentId, String workspaceCode) {
        ReportEntity report = reportDomainService.requireReport(reportId);
        reportDomainService.validateReadableReportWorkspace(report, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(report.getWorkspaceId()).getWorkspaceCode());
        ReportAttachmentEntity attachment = requireAttachment(attachmentId);
        if (!attachment.getReportId().equals(reportId)) {
            throw new BadRequestException("附件不属于当前报告");
        }
        reportAttachmentMapper.deleteById(attachmentId);
        reportAttachmentStorageService.delete(attachment.getStoredPath());
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    public ReportFileDownload downloadReportAttachment(Long reportId, Long attachmentId, String workspaceCode) {
        ReportEntity report = reportDomainService.requireReport(reportId);
        reportDomainService.validateReadableReportWorkspace(report, workspaceCode);
        ReportAttachmentEntity attachment = requireAttachment(attachmentId);
        if (!attachment.getReportId().equals(reportId)) {
            throw new BadRequestException("附件不属于当前报告");
        }
        return reportAttachmentStorageService.load(attachment);
    }

    public ReportAttachmentEntity requireAttachment(Long attachmentId) {
        ReportAttachmentEntity attachment = reportAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new NotFoundException("附件不存在");
        }
        return attachment;
    }
}
