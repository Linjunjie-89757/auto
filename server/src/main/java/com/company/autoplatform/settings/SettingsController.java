package com.company.autoplatform.settings;

import com.company.autoplatform.common.ApiResponse;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceScope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/envs")
    public ApiResponse<PageResponse<EnvConfigItem>> listEnvs(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(settingsService.listEnvs(workspaceCode));
    }

    @PostMapping("/envs")
    public ApiResponse<EnvConfigItem> createEnv(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody CreateEnvConfigRequest request
    ) {
        return ApiResponse.ok(settingsService.createEnv(workspaceCode, request), "环境创建成功");
    }

    @PutMapping("/envs/{id}")
    public ApiResponse<EnvConfigItem> updateEnv(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody CreateEnvConfigRequest request
    ) {
        return ApiResponse.ok(settingsService.updateEnv(id, workspaceCode, request), "环境更新成功");
    }

    @PutMapping("/envs/{id}/status")
    public ApiResponse<EnvConfigItem> updateEnvStatus(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody UpdateSettingStatusRequest request
    ) {
        return ApiResponse.ok(settingsService.updateEnvStatus(id, workspaceCode, request), "环境状态更新成功");
    }

    @DeleteMapping("/envs/{id}")
    public ApiResponse<Void> deleteEnv(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        settingsService.deleteEnv(id, workspaceCode);
        return ApiResponse.ok(null, "环境删除成功");
    }

    @GetMapping("/params")
    public ApiResponse<PageResponse<ParamSetItem>> listParams(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        return ApiResponse.ok(settingsService.listParams(workspaceCode));
    }

    @PostMapping("/params")
    public ApiResponse<ParamSetItem> createParam(
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody CreateParamSetRequest request
    ) {
        return ApiResponse.ok(settingsService.createParam(workspaceCode, request), "参数集创建成功");
    }

    @PutMapping("/params/{id}")
    public ApiResponse<ParamSetItem> updateParam(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody CreateParamSetRequest request
    ) {
        return ApiResponse.ok(settingsService.updateParam(id, workspaceCode, request), "参数集更新成功");
    }

    @PutMapping("/params/{id}/status")
    public ApiResponse<ParamSetItem> updateParamStatus(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode,
            @Valid @RequestBody UpdateSettingStatusRequest request
    ) {
        return ApiResponse.ok(settingsService.updateParamStatus(id, workspaceCode, request), "参数集状态更新成功");
    }

    @DeleteMapping("/params/{id}")
    public ApiResponse<Void> deleteParam(
            @PathVariable Long id,
            @RequestHeader(value = WorkspaceScope.HEADER, required = false) String workspaceCode
    ) {
        settingsService.deleteParam(id, workspaceCode);
        return ApiResponse.ok(null, "参数集删除成功");
    }
}
