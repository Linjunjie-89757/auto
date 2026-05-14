package com.company.autoplatform.bug;

import org.springframework.core.io.Resource;

record BugFileDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize
) {
}
