package com.company.autoplatform.ai;

import org.springframework.core.io.Resource;

record AiRequirementAssetDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize
) {
}
