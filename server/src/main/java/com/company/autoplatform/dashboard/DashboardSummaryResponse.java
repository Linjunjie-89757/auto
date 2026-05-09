package com.company.autoplatform.dashboard;

import java.util.List;

public record DashboardSummaryResponse(
        String workspaceCode,
        String workspaceName,
        List<MetricCard> metrics,
        List<EngineOverview> engineOverviews,
        List<RecentActivity> recentActivities
) {

    public record MetricCard(String label, String value, String trend) {
    }

    public record EngineOverview(String engineType, String label, String detail, String passRate, String tone) {
    }

    public record RecentActivity(String title, String meta, String status, String tone) {
    }
}
