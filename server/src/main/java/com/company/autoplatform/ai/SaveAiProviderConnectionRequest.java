package com.company.autoplatform.ai;

import jakarta.validation.constraints.NotBlank;

public record SaveAiProviderConnectionRequest(
        String workspaceCode,
        @NotBlank(message = "AI 连接名称不能为空") String connectionName,
        @NotBlank(message = "AI 协议类型不能为空") String protocolType,
        @NotBlank(message = "AI API URL 不能为空") String baseUrl,
        String apiKey,
        Integer status
) {
}
