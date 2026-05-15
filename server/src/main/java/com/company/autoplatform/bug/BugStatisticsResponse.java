package com.company.autoplatform.bug;

public record BugStatisticsResponse(
        long total,
        long todo,
        long assigned,
        long inProgress,
        long pendingVerify,
        long closed,
        long rejected
) {
}
