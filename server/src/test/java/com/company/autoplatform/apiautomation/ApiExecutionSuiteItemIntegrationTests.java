package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.workspace.WorkspaceScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiExecutionSuiteItemIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiAutomationService apiAutomationService;

    @Test
    void suiteItemsCanArrangeCasesAndScenarios() throws Exception {
        String unique = "suite-items-" + System.nanoTime();
        int suiteId = createSuite(unique);
        restoreCurrentUser();
        ApiDefinitionCaseDetail apiCase = createCase(unique);
        ApiScenarioDetail scenario = createScenario(unique);

        int caseItemId = addItem(suiteId, "API_CASE", apiCase.id(), true);
        int scenarioItemId = addItem(suiteId, "SCENARIO", scenario.id(), true);

        mockMvc.perform(get("/api/automation/api/execution-suites/{id}/items", suiteId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", contains(caseItemId, scenarioItemId)))
                .andExpect(jsonPath("$.data[*].itemType", contains("API_CASE", "SCENARIO")))
                .andExpect(jsonPath("$.data[*].itemName", contains(apiCase.name(), scenario.name())));

        mockMvc.perform(put("/api/automation/api/execution-suites/{id}/items/reorder", suiteId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"id":%d,"sortOrder":20,"enabled":false},
                                  {"id":%d,"sortOrder":10,"enabled":true}
                                ]}
                                """.formatted(caseItemId, scenarioItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", contains(scenarioItemId, caseItemId)))
                .andExpect(jsonPath("$.data[*].enabled", contains(true, false)));

        mockMvc.perform(delete("/api/automation/api/execution-suites/{suiteId}/items/{itemId}", suiteId, caseItemId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/automation/api/execution-suites/{id}/items", suiteId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(scenarioItemId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(caseItemId))));
    }

    private int createSuite(String unique) throws Exception {
        String response = mockMvc.perform(post("/api/automation/api/execution-suites")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceCode":"risk-ops","name":"%s-suite"}
                                """.formatted(unique)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
    }

    private int addItem(int suiteId, String itemType, Long itemId, boolean enabled) throws Exception {
        String response = mockMvc.perform(post("/api/automation/api/execution-suites/{id}/items", suiteId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemType":"%s","itemId":%d,"enabled":%s}
                                """.formatted(itemType, itemId, enabled)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
    }

    private ApiDefinitionCaseDetail createCase(String unique) {
        ApiDefinitionDetail definition = apiAutomationService.createDefinition(WORKSPACE_CODE, new SaveApiDefinitionRequest(
                WORKSPACE_CODE,
                unique + "-definition",
                null,
                "suite item definition",
                List.of("suite-item"),
                requestConfig("GET", "/suite-item/" + unique),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        return apiAutomationService.createCase(WORKSPACE_CODE, new SaveApiDefinitionCaseRequest(
                WORKSPACE_CODE,
                definition.id(),
                unique + "-case",
                "suite item case",
                List.of("suite-item"),
                requestConfig("GET", "/suite-item/" + unique),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    private ApiScenarioDetail createScenario(String unique) {
        return apiAutomationService.createScenario(WORKSPACE_CODE, new SaveApiScenarioRequest(
                WORKSPACE_CODE,
                unique + "-scenario",
                null,
                null,
                "P1",
                "ACTIVE",
                "suite item scenario",
                List.of("suite-item"),
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(new ApiScenarioStepInput(
                        null,
                        "Script placeholder",
                        "SCRIPT",
                        null,
                        null,
                        true,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "return true;",
                        List.of()
                ))
        ));
    }

    private ApiRequestConfigInput requestConfig(String method, String path) {
        return new ApiRequestConfigInput(
                method,
                path,
                5000,
                List.of(),
                List.of(),
                List.of(),
                new ApiRequestBodyInput("NONE", null, List.of(), null, null, null),
                new ApiAuthConfigInput("NONE", null, null)
        );
    }

    private void restoreCurrentUser() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                11L,
                "zhangli",
                "Zhang Li",
                "{noop}123456",
                PlatformRole.PLATFORM_ADMIN,
                1
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities())
        );
    }
}
