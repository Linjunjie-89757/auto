package com.company.autoplatform.execution;

record StoredReportFile(
        String storedPath,
        String contentType,
        long fileSize
) {
}
