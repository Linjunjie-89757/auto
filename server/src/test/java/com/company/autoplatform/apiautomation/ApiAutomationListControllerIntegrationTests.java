package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.workspace.WorkspaceScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiAutomationListControllerIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiAutomationService apiAutomationService;

    @Test
    void listDefinitionsWithoutParamsKeepsCompatibleLoading() throws Exception {
        String unique = uniquePrefix("defs-compatible");
        ApiDefinitionDetail definition = createDefinition(
                unique + "-definition",
                unique + "-module",
                "GET",
                "/api/" + unique + "/resource",
                "compatible"
        );

        mockMvc.perform(get("/api/automation/api/definitions")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(definition.id().intValue())));
    }

    @Test
    void listDefinitionsSupportsKeywordModuleAndPaginationFilters() throws Exception {
        String unique = uniquePrefix("defs");
        ApiDefinitionModuleItem parentModule = apiAutomationService.createDefinitionModule(
                WORKSPACE_CODE,
                new ApiDefinitionModuleRequest(WORKSPACE_CODE, null, unique + "-parent")
        );
        ApiDefinitionModuleItem childModule = apiAutomationService.createDefinitionModule(
                WORKSPACE_CODE,
                new ApiDefinitionModuleRequest(WORKSPACE_CODE, parentModule.id(), unique + "-child")
        );

        ApiDefinitionDetail first = createDefinition(
                unique + "-alpha",
                childModule.fullPath(),
                "GET",
                "/api/" + unique + "/alpha",
                "tag-alpha"
        );
        ApiDefinitionDetail second = createDefinition(
                unique + "-beta",
                childModule.fullPath(),
                "POST",
                "/api/" + unique + "/beta",
                "tag-beta"
        );
        ApiDefinitionDetail outsideModule = createDefinition(
                unique + "-gamma",
                unique + "-other",
                "GET",
                "/api/" + unique + "/gamma",
                "tag-alpha"
        );

        mockMvc.perform(get("/api/automation/api/definitions")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", "tag-alpha")
                        .param("moduleId", childModule.id().toString())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first.id().intValue()))
                .andExpect(jsonPath("$.data.items[0].directoryName").value(childModule.fullPath()))
                .andExpect(jsonPath("$.data.items[0].tags", hasItem("tag-alpha")))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(outsideModule.id().intValue()))));

        mockMvc.perform(get("/api/automation/api/definitions")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("moduleId", childModule.id().toString())
                        .param("pageNo", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first.id().intValue()));
    }

    @Test
    void listCasesSupportsDefinitionKeywordAndPaginationFilters() throws Exception {
        String unique = uniquePrefix("cases");
        ApiDefinitionDetail definition = createDefinition(
                unique + "-definition",
                unique + "-module",
                "GET",
                "/api/" + unique + "/resource",
                "case-list"
        );
        ApiDefinitionDetail otherDefinition = createDefinition(
                unique + "-other-definition",
                unique + "-module",
                "GET",
                "/api/" + unique + "/other",
                "case-list"
        );
        ApiDefinitionCaseDetail first = createCase(definition.id(), unique + "-alpha-case", "case-alpha");
        ApiDefinitionCaseDetail second = createCase(definition.id(), unique + "-beta-case", "case-alpha");
        ApiDefinitionCaseDetail otherDefinitionCase = createCase(otherDefinition.id(), unique + "-other-case", "case-alpha");
        createCase(definition.id(), unique + "-gamma-case", "case-gamma");

        mockMvc.perform(get("/api/automation/api/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("definitionId", definition.id().toString())
                        .param("keyword", "case-alpha")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNo").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[*].id", containsInAnyOrder(first.id().intValue(), second.id().intValue())))
                .andExpect(jsonPath("$.data.items[*].definitionId", containsInAnyOrder(definition.id().intValue(), definition.id().intValue())))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(otherDefinitionCase.id().intValue()))));

        mockMvc.perform(get("/api/automation/api/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("definitionId", definition.id().toString())
                        .param("keyword", "case-alpha")
                        .param("pageNo", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first.id().intValue()));
    }

    private ApiDefinitionDetail createDefinition(
            String name,
            String directoryName,
            String method,
            String path,
            String tag
    ) {
        return apiAutomationService.createDefinition(WORKSPACE_CODE, new SaveApiDefinitionRequest(
                WORKSPACE_CODE,
                name,
                directoryName,
                "list regression",
                List.of(tag),
                requestConfig(method, path),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    private ApiDefinitionCaseDetail createCase(Long definitionId, String name, String tag) {
        return apiAutomationService.createCase(WORKSPACE_CODE, new SaveApiDefinitionCaseRequest(
                WORKSPACE_CODE,
                definitionId,
                name,
                "list regression " + tag,
                List.of(tag),
                requestConfig("GET", "/case/" + name),
                List.of(),
                List.of(),
                List.of()
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

    private String uniquePrefix(String label) {
        return "api-list-" + label + "-" + System.nanoTime();
    }
}
