package com.company.autoplatform.seed;

import com.company.autoplatform.bug.BugPriority;
import com.company.autoplatform.bug.BugSeverity;
import com.company.autoplatform.bug.BugSourceType;
import com.company.autoplatform.bug.BugStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DemoDataStore {

    public final Map<String, WorkspaceRecord> workspaces = new LinkedHashMap<>();
    public final Map<Long, UserRecord> users = new LinkedHashMap<>();
    public final Map<Long, CaseRecord> cases = new LinkedHashMap<>();
    public final Map<Long, TaskRecord> tasks = new LinkedHashMap<>();
    public final Map<Long, ReportRecord> reports = new LinkedHashMap<>();
    public final Map<Long, BugRecord> bugs = new LinkedHashMap<>();
    public final Map<Long, List<BugFlowRecord>> bugFlows = new LinkedHashMap<>();
    public final Map<Long, List<BugCommentRecord>> bugComments = new LinkedHashMap<>();

    private final AtomicLong bugIdSequence = new AtomicLong(1000);
    private final AtomicLong flowIdSequence = new AtomicLong(5000);
    private final AtomicLong commentIdSequence = new AtomicLong(8000);

    @PostConstruct
    void init() {
        workspaces.put("account-open", new WorkspaceRecord(1L, "account-open", "开户工作空间", "开户链路相关测试资产"));
        workspaces.put("trade-core", new WorkspaceRecord(2L, "trade-core", "交易工作空间", "交易核心流程"));
        workspaces.put("risk-control", new WorkspaceRecord(3L, "risk-control", "风控工作空间", "风控校验和拦截"));

        users.put(1L, new UserRecord(1L, "zhangli", "张莉"));
        users.put(2L, new UserRecord(2L, "chennan", "陈楠"));
        users.put(3L, new UserRecord(3L, "liping", "李萍"));
        users.put(4L, new UserRecord(4L, "zhaofeng", "赵峰"));
        users.put(5L, new UserRecord(5L, "wangxin", "王欣"));

        cases.put(128L, new CaseRecord(128L, "CASE-00128", "开户成功主流程", "FUNCTION", "P0", "AI生成", "已确认", 2L, "account-open"));
        cases.put(129L, new CaseRecord(129L, "CASE-00129", "开户字段边界长度校验", "BOUNDARY", "P1", "AI追加", "草稿", 3L, "account-open"));
        cases.put(130L, new CaseRecord(130L, "CASE-00130", "短信验证码失效处理", "EXCEPTION", "P0", "手工创建", "已确认", 4L, "trade-core"));
        cases.put(131L, new CaseRecord(131L, "CASE-00131", "多浏览器登录兼容回归", "REGRESSION", "P2", "导入", "已归档", 5L, "risk-control"));

        tasks.put(201L, new TaskRecord(201L, "api-trade-settlement", "API", "SUCCESS", "UAT 环境下单与结算回归", "trade-core"));
        tasks.put(202L, new TaskRecord(202L, "web-regression-payment", "WEB", "FAILED", "Chrome Headless 支付回归", "trade-core"));
        tasks.put(203L, new TaskRecord(203L, "app-login-and-transfer", "APP", "FAILED", "iPhone 14 登录与转账冒烟", "risk-control"));

        reports.put(301L, new ReportRecord(301L, 201L, "api-trade-settlement-report", "SUCCESS", "鉴权和结算断言全部通过", "trade-core"));
        reports.put(302L, new ReportRecord(302L, 202L, "web-regression-payment-report", "FAILED", "支付按钮等待超时", "trade-core"));
        reports.put(303L, new ReportRecord(303L, 203L, "app-login-and-transfer-report", "FAILED", "登录后首页元素未出现", "risk-control"));

        BugRecord bug1 = new BugRecord(
                1001L,
                "BUG-20260502-001",
                "支付页面提交按钮偶现不可点击",
                "Playwright 回归时支付页按钮在弹层后不可点击，导致流程中断。",
                BugPriority.P1,
                BugSeverity.HIGH,
                BugStatus.IN_PROGRESS,
                BugSourceType.REPORT,
                4L,
                1L,
                130L,
                302L,
                202L,
                List.of("支付", "Web", "回归"),
                "trade-core"
        );
        BugRecord bug2 = new BugRecord(
                1002L,
                "BUG-20260502-002",
                "开户验证码边界校验提示不一致",
                "边界用例中验证码过期和验证码错误返回的前端提示文案不一致。",
                BugPriority.P2,
                BugSeverity.MEDIUM,
                BugStatus.TODO,
                BugSourceType.CASE,
                3L,
                1L,
                129L,
                null,
                null,
                List.of("开户", "边界"),
                "account-open"
        );
        bugs.put(bug1.id(), bug1);
        bugs.put(bug2.id(), bug2);

        bugFlows.put(1001L, new ArrayList<>(List.of(
                new BugFlowRecord(nextFlowId(), BugStatus.TODO, BugStatus.ASSIGNED, 1L, "指派给赵峰跟进", LocalDateTime.now().minusHours(4)),
                new BugFlowRecord(nextFlowId(), BugStatus.ASSIGNED, BugStatus.IN_PROGRESS, 4L, "已复现并开始修复", LocalDateTime.now().minusHours(2))
        )));
        bugFlows.put(1002L, new ArrayList<>());

        bugComments.put(1001L, new ArrayList<>(List.of(
                new BugCommentRecord(nextCommentId(), "弹层关闭后即可恢复，怀疑遮罩层状态没清理。", 4L, LocalDateTime.now().minusHours(2)),
                new BugCommentRecord(nextCommentId(), "已补充截图，等前端同学确认。", 1L, LocalDateTime.now().minusMinutes(45))
        )));
        bugComments.put(1002L, new ArrayList<>());

        bugIdSequence.set(1002L);
    }

    public long nextBugId() {
        return bugIdSequence.incrementAndGet();
    }

    public long nextFlowId() {
        return flowIdSequence.incrementAndGet();
    }

    public long nextCommentId() {
        return commentIdSequence.incrementAndGet();
    }

    public record WorkspaceRecord(Long id, String code, String name, String description) {
    }

    public record UserRecord(Long id, String username, String displayName) {
    }

    public record CaseRecord(Long id, String caseNo, String title, String caseType, String priority, String sourceType,
                             String status, Long ownerId, String workspaceCode) {
    }

    public record TaskRecord(Long id, String taskName, String engineType, String status, String summary, String workspaceCode) {
    }

    public record ReportRecord(Long id, Long taskId, String reportName, String result, String failureSummary, String workspaceCode) {
    }

    public record BugRecord(
            Long id,
            String bugNo,
            String title,
            String description,
            BugPriority priority,
            BugSeverity severity,
            BugStatus status,
            BugSourceType sourceType,
            Long assigneeId,
            Long reporterId,
            Long relatedCaseId,
            Long relatedReportId,
            Long relatedTaskId,
            List<String> tags,
            String workspaceCode
    ) {
    }

    public record BugFlowRecord(Long id, BugStatus fromStatus, BugStatus toStatus, Long operatorId, String actionComment,
                                LocalDateTime createdAt) {
    }

    public record BugCommentRecord(Long id, String content, Long commenterId, LocalDateTime createdAt) {
    }
}
