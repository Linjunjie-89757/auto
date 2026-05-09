package com.company.autoplatform.execution;

public record UpdateReportContentRequest(
        String failureSummary,
        String logText,
        String logSource
) {
}
