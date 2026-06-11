package com.company.autoplatform.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.workspace.WorkspaceScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ExecutionControllerIntegrationTests extends IntegrationTestSupport {

    private static final String RISK_OPS = "risk-ops";
    private static final String PAYMENTS_CORE = "payments-core";
    private static final String RETAIL_ONBOARDING = "retail-onboarding";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listTasksWithoutPaginationReturnsAllMatchedTasks() throws Exception {
        String unique = uniquePrefix("no-page");
        TaskSummaryResponse first = createTask(RISK_OPS, unique + "-first", "API", "SUCCESS");
        TaskSummaryResponse second = createTask(RISK_OPS, unique + "-second", "WEB", "FAILED");

        mockMvc.perform(get("/api/tasks")
                        .header(WorkspaceScope.HEADER, RISK_OPS)
                        .param("keyword", unique))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[*].id").value(containsInAnyOrder(
                        first.id().intValue(),
                        second.id().intValue()
                )));
    }

    @Test
    void listTasksWithPaginationReturnsPageMetadata() throws Exception {
        String unique = uniquePrefix("page");
        createTask(RISK_OPS, unique + "-first", "API", "SUCCESS");
        createTask(RISK_OPS, unique + "-second", "API", "SUCCESS");
        TaskSummaryResponse third = createTask(RISK_OPS, unique + "-third", "API", "SUCCESS");

        mockMvc.perform(get("/api/tasks")
                        .header(WorkspaceScope.HEADER, RISK_OPS)
                        .param("keyword", unique)
                        .param("pageNo", "2")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(third.id().intValue()));
    }

    @Test
    void listTasksSupportsKeywordStatusAndEngineTypeFiltersTogether() throws Exception {
        String unique = uniquePrefix("filters");
        TaskSummaryResponse expected = createTask(RISK_OPS, unique + "-api-success", "API", "SUCCESS");
        createTask(RISK_OPS, unique + "-api-failed", "API", "FAILED");
        createTask(RISK_OPS, unique + "-web-success", "WEB", "SUCCESS");

        mockMvc.perform(get("/api/tasks")
                        .header(WorkspaceScope.HEADER, RISK_OPS)
                        .param("keyword", unique)
                        .param("status", "success")
                        .param("engineType", "api")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(expected.id().intValue()))
                .andExpect(jsonPath("$.data.items[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].engineType").value("API"));
    }

    @Test
    void listTasksForMemberOnlyReturnsReadableWorkspaceTasks() throws Exception {
        String unique = uniquePrefix("scope");
        TaskSummaryResponse readable = createTask(RETAIL_ONBOARDING, unique + "-readable", "API", "SUCCESS");
        TaskSummaryResponse hidden = createTask(PAYMENTS_CORE, unique + "-hidden", "API", "SUCCESS");

        mockMvc.perform(get("/api/tasks")
                        .with(authentication(memberAuthentication(12L, "chennan")))
                        .header(WorkspaceScope.HEADER, WorkspaceScope.ALL)
                        .param("keyword", unique))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(readable.id().intValue())))
                .andExpect(jsonPath("$.data.items[*].id").value(not(hasItem(hidden.id().intValue()))))
                .andExpect(jsonPath("$.data.items[*].workspaceCode", everyItem(startsWith(RETAIL_ONBOARDING))));
    }

    @Test
    void reportCrudKeepsResponseShapeAndContentUpdateBehavior() throws Exception {
        String unique = uniquePrefix("report-crud");
        TaskSummaryResponse task = createTask(RISK_OPS, unique + "-task", "API", "SUCCESS");

        String createBody = """
                {
                  "workspaceCode": "%s",
                  "taskId": %d,
                  "reportName": "%s-report",
                  "result": "SUCCESS",
                  "logSource": "api",
                  "failureSummary": ""
                }
                """.formatted(RISK_OPS, task.id(), unique);

        Integer reportId = createReport(createBody, RISK_OPS);

        mockMvc.perform(get("/api/reports/{id}", reportId)
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.taskId").value(task.id().intValue()))
                .andExpect(jsonPath("$.data.taskName").value(unique + "-task"))
                .andExpect(jsonPath("$.data.reportName").value(unique + "-report"))
                .andExpect(jsonPath("$.data.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.logSource").value("API"))
                .andExpect(jsonPath("$.data.workspaceCode").value(RISK_OPS))
                .andExpect(jsonPath("$.data.attachments.length()").value(0));

        mockMvc.perform(get("/api/reports")
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(reportId)));

        String contentBody = """
                {
                  "failureSummary": "failed reason",
                  "logText": "line 1\\nline 2",
                  "logSource": "manual"
                }
                """;

        mockMvc.perform(put("/api/reports/{id}/content", reportId)
                        .header(WorkspaceScope.HEADER, RISK_OPS)
                        .contentType("application/json")
                        .content(contentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.failureSummary").value("failed reason"))
                .andExpect(jsonPath("$.data.logText").value("line 1\nline 2"))
                .andExpect(jsonPath("$.data.logSource").value("MANUAL"));
    }

    @Test
    void createReportRejectsTaskFromDifferentWorkspace() throws Exception {
        String unique = uniquePrefix("report-scope");
        TaskSummaryResponse task = createTask(PAYMENTS_CORE, unique + "-task", "API", "SUCCESS");
        String createBody = """
                {
                  "workspaceCode": "%s",
                  "taskId": %d,
                  "reportName": "%s-report",
                  "result": "SUCCESS",
                  "logSource": "API",
                  "failureSummary": null
                }
                """.formatted(RISK_OPS, task.id(), unique);

        mockMvc.perform(post("/api/reports")
                        .header(WorkspaceScope.HEADER, RISK_OPS)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reportAttachmentUploadDownloadAndDeleteKeepsMainFlow() throws Exception {
        String unique = uniquePrefix("attachment");
        TaskSummaryResponse task = createTask(RISK_OPS, unique + "-task", "API", "SUCCESS");
        Integer reportId = createReport("""
                {
                  "workspaceCode": "%s",
                  "taskId": %d,
                  "reportName": "%s-report",
                  "result": "SUCCESS",
                  "logSource": "API",
                  "failureSummary": null
                }
                """.formatted(RISK_OPS, task.id(), unique), RISK_OPS);

        MockMultipartFile file = new MockMultipartFile(
                "files",
                "evidence.txt",
                "text/plain",
                "attachment evidence".getBytes(StandardCharsets.UTF_8)
        );

        String uploadResponse = mockMvc.perform(multipart("/api/reports/{id}/attachments", reportId)
                        .file(file)
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].fileName").value("evidence.txt"))
                .andExpect(jsonPath("$.data[0].contentType").value("text/plain"))
                .andExpect(jsonPath("$.data[0].fileSize").value(19))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Integer attachmentId = objectMapper.readTree(uploadResponse).path("data").get(0).path("id").asInt();

        mockMvc.perform(get("/api/reports/{id}", reportId)
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachments.length()").value(1))
                .andExpect(jsonPath("$.data.attachments[0].id").value(attachmentId))
                .andExpect(jsonPath("$.data.attachments[0].downloadUrl")
                        .value("/api/reports/" + reportId + "/attachments/" + attachmentId + "/download"));

        mockMvc.perform(get("/api/reports/{id}/attachments/{attachmentId}/download", reportId, attachmentId)
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(content().string("attachment evidence"));

        mockMvc.perform(delete("/api/reports/{id}/attachments/{attachmentId}", reportId, attachmentId)
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/reports/{id}", reportId)
                        .header(WorkspaceScope.HEADER, RISK_OPS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachments.length()").value(0));
    }

    private TaskSummaryResponse createTask(String workspaceCode, String taskName, String engineType, String status) {
        return executionService.createTask(workspaceCode, new CreateTaskRequest(
                workspaceCode,
                taskName,
                engineType,
                status,
                "created by integration test"
        ));
    }

    private Integer createReport(String body, String workspaceCode) throws Exception {
        String response = mockMvc.perform(post("/api/reports")
                        .header(WorkspaceScope.HEADER, workspaceCode)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asInt();
    }

    private UsernamePasswordAuthenticationToken memberAuthentication(Long userId, String username) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                userId,
                username,
                username,
                "{noop}123456",
                PlatformRole.MEMBER,
                1
        );
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private String uniquePrefix(String label) {
        return "execution-" + label + "-" + System.nanoTime();
    }

}
