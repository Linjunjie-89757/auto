package com.company.autoplatform.execution;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.workspace.WorkspaceScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private TaskSummaryResponse createTask(String workspaceCode, String taskName, String engineType, String status) {
        return executionService.createTask(workspaceCode, new CreateTaskRequest(
                workspaceCode,
                taskName,
                engineType,
                status,
                "created by integration test"
        ));
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
