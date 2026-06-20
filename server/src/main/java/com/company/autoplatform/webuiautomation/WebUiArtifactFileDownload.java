package com.company.autoplatform.webuiautomation;

import org.springframework.core.io.Resource;

record WebUiArtifactFileDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize
) {
}
