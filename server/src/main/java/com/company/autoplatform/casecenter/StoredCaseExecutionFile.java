package com.company.autoplatform.casecenter;

record StoredCaseExecutionFile(
        String storedPath,
        String contentType,
        long fileSize
) {
}
