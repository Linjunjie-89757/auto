package com.company.autoplatform.execution;

import org.springframework.core.io.Resource;

record ReportFileDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize
) {
}
