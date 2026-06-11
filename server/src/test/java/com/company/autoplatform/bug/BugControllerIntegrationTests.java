package com.company.autoplatform.bug;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.casecenter.CaseService;
import com.company.autoplatform.casecenter.CaseSummaryResponse;
import com.company.autoplatform.casecenter.CreateCaseRequest;
import com.company.autoplatform.execution.CreateReportRequest;
import com.company.autoplatform.execution.CreateTaskRequest;
import com.company.autoplatform.execution.ExecutionService;
import com.company.autoplatform.execution.ReportSummaryResponse;
import com.company.autoplatform.execution.TaskSummaryResponse;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BugControllerIntegrationTests extends IntegrationTestSupport {

    private static final Long ASSIGNEE_ID = 11L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaseService caseService;

    @Autowired
    private ExecutionService executionService;

    @Test
    void createAndGetBugKeepsDetailResponseShape() throws Exception {
        String unique = uniquePrefix("create-detail");

        Integer bugId = createBug(unique + "-manual", "P1", "HIGH", null, null, null);

        mockMvc.perform(get("/api/bugs/{id}", bugId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(bugId))
                .andExpect(jsonPath("$.data.title").value(unique + "-manual"))
                .andExpect(jsonPath("$.data.priority").value("P1"))
                .andExpect(jsonPath("$.data.severity").value("HIGH"))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.assigneeId").value(ASSIGNEE_ID.intValue()))
                .andExpect(jsonPath("$.data.reporterId").value(11))
                .andExpect(jsonPath("$.data.workspaceCode").value(WORKSPACE_CODE))
                .andExpect(jsonPath("$.data.tags", hasItem("bug-regression")))
                .andExpect(jsonPath("$.data.attachments.length()").value(0))
                .andExpect(jsonPath("$.data.sourceContext.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.sourceContext.caseSummary").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceContext.reportSummary").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceContext.taskSummary").value(nullValue()))
                .andExpect(jsonPath("$.data.flows.length()").value(1))
                .andExpect(jsonPath("$.data.flows[0].fromStatus").value("TODO"))
                .andExpect(jsonPath("$.data.flows[0].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.comments.length()").value(0))
                .andExpect(jsonPath("$.data.activities[*].type", hasItem("CREATED")))
                .andExpect(jsonPath("$.data.activities[*].type", hasItem("ASSIGNED")));
    }

    @Test
    void listBugsSupportsPaginationAndFilters() throws Exception {
        String unique = uniquePrefix("list-filter");
        Integer first = createBug(unique + "-p1-high-first", "P1", "HIGH", null, null, null);
        Integer second = createBug(unique + "-p1-high-second", "P1", "HIGH", null, null, null);
        createBug(unique + "-p2-medium", "P2", "MEDIUM", null, null, null);

        mockMvc.perform(get("/api/bugs")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("priority", "p1")
                        .param("severity", "high")
                        .param("status", "assigned")
                        .param("pageNo", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[*].id", containsInAnyOrder(first, second)))
                .andExpect(jsonPath("$.data.items[*].priority", containsInAnyOrder("P1", "P1")))
                .andExpect(jsonPath("$.data.items[*].severity", containsInAnyOrder("HIGH", "HIGH")))
                .andExpect(jsonPath("$.data.items[*].status", containsInAnyOrder("ASSIGNED", "ASSIGNED")));
    }

    @Test
    void createBugFromCaseAndReportKeepsSourceContextShape() throws Exception {
        String unique = uniquePrefix("source");
        CaseSummaryResponse relatedCase = createCase(unique + "-case");
        TaskSummaryResponse relatedTask = createTask(unique + "-task");
        ReportSummaryResponse relatedReport = createReport(relatedTask.id(), unique + "-report");

        Integer caseBugId = createBugFromCase(relatedCase.id(), unique + "-case-bug");
        mockMvc.perform(get("/api/bugs/{id}", caseBugId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceType").value("CASE"))
                .andExpect(jsonPath("$.data.relatedCaseId").value(relatedCase.id().intValue()))
                .andExpect(jsonPath("$.data.relatedReportId").value(nullValue()))
                .andExpect(jsonPath("$.data.relatedTaskId").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceContext.sourceType").value("CASE"))
                .andExpect(jsonPath("$.data.sourceContext.caseSummary.id").value(relatedCase.id().intValue()))
                .andExpect(jsonPath("$.data.sourceContext.caseSummary.title").value(unique + "-case"))
                .andExpect(jsonPath("$.data.sourceContext.caseSummary.workspaceCode").value(WORKSPACE_CODE))
                .andExpect(jsonPath("$.data.sourceContext.reportSummary").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceContext.taskSummary").value(nullValue()))
                .andExpect(jsonPath("$.data.flows.length()").value(1))
                .andExpect(jsonPath("$.data.comments.length()").value(0))
                .andExpect(jsonPath("$.data.activities[*].type", hasItem("CREATED")));

        Integer reportBugId = createBugFromReport(relatedReport.id(), unique + "-report-bug");
        mockMvc.perform(get("/api/bugs/{id}", reportBugId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceType").value("REPORT"))
                .andExpect(jsonPath("$.data.relatedCaseId").value(nullValue()))
                .andExpect(jsonPath("$.data.relatedReportId").value(relatedReport.id().intValue()))
                .andExpect(jsonPath("$.data.relatedTaskId").value(relatedTask.id().intValue()))
                .andExpect(jsonPath("$.data.sourceContext.sourceType").value("REPORT"))
                .andExpect(jsonPath("$.data.sourceContext.caseSummary").value(nullValue()))
                .andExpect(jsonPath("$.data.sourceContext.reportSummary.id").value(relatedReport.id().intValue()))
                .andExpect(jsonPath("$.data.sourceContext.reportSummary.reportName").value(unique + "-report"))
                .andExpect(jsonPath("$.data.sourceContext.reportSummary.taskId").value(relatedTask.id().intValue()))
                .andExpect(jsonPath("$.data.sourceContext.taskSummary.id").value(relatedTask.id().intValue()))
                .andExpect(jsonPath("$.data.sourceContext.taskSummary.taskName").value(unique + "-task"))
                .andExpect(jsonPath("$.data.flows.length()").value(1))
                .andExpect(jsonPath("$.data.comments.length()").value(0))
                .andExpect(jsonPath("$.data.activities[*].type", hasItem("CREATED")));
    }

    private Integer createBug(String title, String priority, String severity, Long caseId, Long reportId, Long taskId) throws Exception {
        String response = mockMvc.perform(post("/api/bugs")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(bugRequest(title, priority, severity, caseId, reportId, taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value(title))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asInt();
    }

    private Integer createBugFromCase(Long caseId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/cases/{id}/bugs", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(bugRequest(title, "P2", "MEDIUM", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value(title))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asInt();
    }

    private Integer createBugFromReport(Long reportId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/reports/{id}/bugs", reportId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(bugRequest(title, "P0", "CRITICAL", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value(title))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asInt();
    }

    private String bugRequest(String title, String priority, String severity, Long caseId, Long reportId, Long taskId) {
        return """
                {
                  "workspaceCode": "%s",
                  "title": "%s",
                  "description": "Created by bug integration regression",
                  "priority": "%s",
                  "severity": "%s",
                  "assigneeId": %d,
                  "relatedCaseId": %s,
                  "relatedReportId": %s,
                  "relatedTaskId": %s,
                  "tags": ["bug-regression", "step47"]
                }
                """.formatted(
                WORKSPACE_CODE,
                title,
                priority,
                severity,
                ASSIGNEE_ID,
                jsonNumberOrNull(caseId),
                jsonNumberOrNull(reportId),
                jsonNumberOrNull(taskId)
        );
    }

    private CaseSummaryResponse createCase(String title) {
        return caseService.createCase(WORKSPACE_CODE, new CreateCaseRequest(
                WORKSPACE_CODE,
                null,
                title,
                "FUNCTION",
                "P1",
                "MANUAL",
                "CONFIRMED",
                ASSIGNEE_ID,
                "precondition",
                "steps",
                "expected result"
        ));
    }

    private TaskSummaryResponse createTask(String taskName) {
        return executionService.createTask(WORKSPACE_CODE, new CreateTaskRequest(
                WORKSPACE_CODE,
                taskName,
                "API",
                "SUCCESS",
                "created by bug integration regression"
        ));
    }

    private ReportSummaryResponse createReport(Long taskId, String reportName) {
        return executionService.createReport(WORKSPACE_CODE, new CreateReportRequest(
                WORKSPACE_CODE,
                taskId,
                reportName,
                "FAILED",
                "API",
                "failure summary"
        ));
    }

    private String jsonNumberOrNull(Long value) {
        return value == null ? "null" : value.toString();
    }

    private String uniquePrefix(String label) {
        return "bug-" + label + "-" + System.nanoTime();
    }
}
