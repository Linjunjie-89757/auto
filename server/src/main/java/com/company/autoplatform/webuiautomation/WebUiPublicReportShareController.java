package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static com.company.autoplatform.webuiautomation.WebUiAutomationModels.WebUiSharedReport;

@RestController
@RequestMapping("/api/public/automation/web/report-shares")
public class WebUiPublicReportShareController {

    private final WebUiAutomationService webUiAutomationService;

    public WebUiPublicReportShareController(WebUiAutomationService webUiAutomationService) {
        this.webUiAutomationService = webUiAutomationService;
    }

    @GetMapping("/{token}")
    public ApiResponse<WebUiSharedReport> getSharedReport(@PathVariable String token) {
        return ApiResponse.ok(webUiAutomationService.getSharedReport(token));
    }

    @GetMapping("/{token}/runs/{runId}/artifacts/{artifactId}/download")
    public ResponseEntity<Resource> downloadSharedArtifact(
            @PathVariable String token,
            @PathVariable Long runId,
            @PathVariable Long artifactId
    ) {
        WebUiArtifactFileDownload download = webUiAutomationService.downloadSharedArtifact(token, runId, artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }
}
