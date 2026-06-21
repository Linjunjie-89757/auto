package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.workspace.WorkspaceScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiExecutionSuiteModuleIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void suiteModuleCrudSupportsConcreteWorkspaceAndRejectsAllWorkspaceWrites() throws Exception {
        String unique = "suite-module-" + System.nanoTime();

        int parentId = createModule(null, unique + "-parent");
        int childId = createModule(parentId, unique + "-child");

        mockMvc.perform(get("/api/automation/api/execution-suite-modules")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].id", hasItem(parentId)))
                .andExpect(jsonPath("$.data[*].children[*].id", hasItem(childId)));

        mockMvc.perform(put("/api/automation/api/execution-suite-modules/{id}", childId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceCode":"risk-ops","name":"%s-renamed"}
                                """.formatted(unique)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value(unique + "-renamed"));

        mockMvc.perform(post("/api/automation/api/execution-suite-modules")
                        .header(WorkspaceScope.HEADER, WorkspaceScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceCode":"ALL","name":"%s-all"}
                                """.formatted(unique)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/automation/api/execution-suite-modules/{id}", parentId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/automation/api/execution-suite-modules/{id}", childId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/automation/api/execution-suite-modules")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].children[*].id", not(hasItem(childId))));
    }

    private int createModule(Integer parentId, String name) throws Exception {
        String body = parentId == null
                ? """
                {"workspaceCode":"risk-ops","name":"%s"}
                """.formatted(name)
                : """
                {"workspaceCode":"risk-ops","parentId":%d,"name":"%s"}
                """.formatted(parentId, name);
        String response = mockMvc.perform(post("/api/automation/api/execution-suite-modules")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
    }
}
