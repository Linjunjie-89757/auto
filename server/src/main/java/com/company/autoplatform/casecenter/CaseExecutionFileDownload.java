package com.company.autoplatform.casecenter;

import org.springframework.core.io.Resource;

record CaseExecutionFileDownload(
        Resource resource,
        String fileName,
        String contentType,
        long fileSize
) {
}
