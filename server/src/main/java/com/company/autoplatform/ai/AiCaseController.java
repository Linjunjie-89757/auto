package com.company.autoplatform.ai;

import com.company.autoplatform.common.ApiResponse;
import com.company.autoplatform.workspace.WorkspaceScope;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cases/ai")
public class AiCaseController {

    private final AiCaseService aiCaseService;

    public AiCaseController(AiCaseService aiCaseService) {
        this.aiCaseService = aiCaseService;
    }

    @GetMapping("/config")
    public ApiResponse<AiCaseConfigResponse> getConfig(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestParam(value = "targetWorkspaceCode", required = false) String targetWorkspaceCode
    ) {
        return ApiResponse.ok(aiCaseService.getConfig(workspaceCode, targetWorkspaceCode));
    }

    @PostMapping("/config")
    public ApiResponse<AiCaseConfigItem> createConfig(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveAiCaseConfigRequest request
    ) {
        return ApiResponse.ok(aiCaseService.createConfig(workspaceCode, request), "AI config created");
    }

    @PutMapping("/config/{id}")
    public ApiResponse<AiCaseConfigItem> updateConfig(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveAiCaseConfigRequest request
    ) {
        return ApiResponse.ok(aiCaseService.updateConfig(id, workspaceCode, request), "AI config updated");
    }

    @GetMapping("/config/{id}/secret")
    public ApiResponse<AiCaseConfigSecretResponse> getConfigSecret(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(aiCaseService.getConfigSecret(id, workspaceCode));
    }

    @PostMapping("/config/test")
    public ApiResponse<TestAiCaseConfigResponse> testConfig(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody SaveAiCaseConfigRequest request
    ) {
        return ApiResponse.ok(aiCaseService.testConfig(workspaceCode, request), "AI config connection tested");
    }

    @PostMapping("/review")
    public ApiResponse<AiReviewResult> reviewGeneratedCases(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody ReviewAiGeneratedCasesRequest request
    ) {
        return ApiResponse.ok(aiCaseService.reviewGeneratedCases(workspaceCode, request), "AI cases reviewed");
    }

    @PostMapping("/generate")
    public ApiResponse<GenerateAiCasesResponse> generateCases(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody GenerateAiCasesRequest request
    ) {
        return ApiResponse.ok(aiCaseService.generateCases(workspaceCode, request), "AI cases generated");
    }

    @PostMapping("/requirement-import")
    public ApiResponse<ImportRequirementDocumentResponse> importRequirementDocument(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(aiCaseService.importRequirementDocument(workspaceCode, file), "Requirement document imported");
    }

    @PostMapping("/assets")
    public ApiResponse<java.util.List<AiRequirementAssetResponse>> uploadRequirementAssets(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @RequestPart("files") java.util.List<MultipartFile> files
    ) {
        return ApiResponse.ok(aiCaseService.uploadRequirementAssets(workspaceCode, files), "Requirement assets uploaded");
    }

    @DeleteMapping("/assets/{id}")
    public ApiResponse<Void> deleteRequirementAsset(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        aiCaseService.deleteRequirementAsset(id, workspaceCode);
        return ApiResponse.ok(null, "Requirement asset deleted");
    }

    @GetMapping("/assets/{id}/download")
    public ResponseEntity<Resource> downloadRequirementAsset(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        var file = aiCaseService.downloadRequirementAsset(id, workspaceCode);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.fileSize())
                .body(file.resource());
    }
}
