package com.company.autoplatform.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.company.autoplatform.bug.BugEntity;
import com.company.autoplatform.bug.BugMapper;
import com.company.autoplatform.casecenter.CaseEntity;
import com.company.autoplatform.casecenter.CaseMapper;
import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.execution.TaskEntity;
import com.company.autoplatform.execution.TaskMapper;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final CaseMapper caseMapper;
    private final BugMapper bugMapper;
    private final TaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final WorkspaceService workspaceService;

    public DashboardService(CaseMapper caseMapper, BugMapper bugMapper, TaskMapper taskMapper,
                            ReportMapper reportMapper, WorkspaceService workspaceService) {
        this.caseMapper = caseMapper;
        this.bugMapper = bugMapper;
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.workspaceService = workspaceService;
    }

    public DashboardSummaryResponse summary(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        Long workspaceId = WorkspaceScope.isAll(normalized) ? null : workspaceService.requireReadableWorkspace(normalized).getId();
        List<Long> readableWorkspaceIds = workspaceService.isPlatformAdmin() ? List.of() : workspaceService.listReadableWorkspaceIds();

        long caseCount = countScoped(caseMapper, CaseEntity::getWorkspaceId, workspaceId, readableWorkspaceIds);
        long bugCount = countScoped(bugMapper, BugEntity::getWorkspaceId, workspaceId, readableWorkspaceIds);
        long taskCount = countScoped(taskMapper, TaskEntity::getWorkspaceId, workspaceId, readableWorkspaceIds);
        long reportFailures = reportMapper.selectList(scopeQuery(ReportEntity::getWorkspaceId, workspaceId, readableWorkspaceIds)).stream()
                .filter(item -> "FAILED".equalsIgnoreCase(item.getResult()))
                .count();

        String workspaceName = workspaceId == null ? "全部空间" : workspaceService.requireReadableWorkspace(normalized).getWorkspaceName();

        return new DashboardSummaryResponse(
                normalized,
                workspaceName,
                List.of(
                        new DashboardSummaryResponse.MetricCard("用例总数", String.valueOf(caseCount), workspaceId == null ? "汇总当前账号可见空间的用例规模" : "当前空间用例沉淀"),
                        new DashboardSummaryResponse.MetricCard("缺陷总数", String.valueOf(bugCount), "含待处理、处理中和待验证"),
                        new DashboardSummaryResponse.MetricCard("执行任务数", String.valueOf(taskCount), "接口 / Web / APP 任务统一统计"),
                        new DashboardSummaryResponse.MetricCard("失败报告数", String.valueOf(reportFailures), "用于驱动缺陷闭环")
                ),
                List.of(
                        new DashboardSummaryResponse.EngineOverview("API", "接口自动化", "Karate 场景与断言执行", "97.1%", "success"),
                        new DashboardSummaryResponse.EngineOverview("WEB", "Web UI 自动化", "Playwright 调试产物完备", "92.8%", "warning"),
                        new DashboardSummaryResponse.EngineOverview("APP", "APP 自动化", "设备心跳与执行波动并存", "89.3%", "danger")
                ),
                List.of(
                        new DashboardSummaryResponse.RecentActivity("AI 补充开户流程边界用例", "开户工作空间 · 18 分钟前", "待评审", "warning"),
                        new DashboardSummaryResponse.RecentActivity("回归场景 smoke-web-042 执行完成", "交易工作空间 · 26 分钟前", "成功", "success"),
                        new DashboardSummaryResponse.RecentActivity("从失败报告创建缺陷 BUG-20260502-001", "交易工作空间 · 42 分钟前", "处理中", "danger")
                )
        );
    }

    private <T> long countScoped(BaseMapper<T> mapper, SFunction<T, Long> field, Long workspaceId, List<Long> readableWorkspaceIds) {
        return mapper.selectCount(scopeQuery(field, workspaceId, readableWorkspaceIds));
    }

    private <T> LambdaQueryWrapper<T> scopeQuery(SFunction<T, Long> field, Long workspaceId, List<Long> readableWorkspaceIds) {
        LambdaQueryWrapper<T> query = new LambdaQueryWrapper<>();
        if (workspaceId != null) {
            query.eq(field, workspaceId);
        } else if (!readableWorkspaceIds.isEmpty()) {
            query.in(field, readableWorkspaceIds);
        }
        return query;
    }
}
