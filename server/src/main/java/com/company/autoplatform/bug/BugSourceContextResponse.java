package com.company.autoplatform.bug;

public record BugSourceContextResponse(
        BugSourceType sourceType,
        BugCaseSummaryResponse caseSummary,
        BugReportSummaryResponse reportSummary,
        BugTaskSummaryResponse taskSummary
) {
}
