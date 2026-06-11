package com.company.autoplatform.casecenter;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CaseControllerIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createGetUpdateDeleteCaseKeepsResponseShape() throws Exception {
        String unique = uniquePrefix("crud");
        Integer caseId = createCase(unique + "-case", "P1", null);

        mockMvc.perform(get("/api/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(caseId))
                .andExpect(jsonPath("$.data.title").value(unique + "-case"))
                .andExpect(jsonPath("$.data.caseType").value("FUNCTION"))
                .andExpect(jsonPath("$.data.priority").value("P1"))
                .andExpect(jsonPath("$.data.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.executionStatus").value("NOT_RUN"))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.workspaceCode").value(WORKSPACE_CODE))
                .andExpect(jsonPath("$.data.attachments.length()").value(0));

        mockMvc.perform(put("/api/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(caseRequest(unique + "-updated", "P2", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(caseId))
                .andExpect(jsonPath("$.data.title").value(unique + "-updated"))
                .andExpect(jsonPath("$.data.priority").value("P2"));

        mockMvc.perform(delete("/api/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listCasesSupportsPaginationAndFilters() throws Exception {
        String unique = uniquePrefix("list");
        Integer first = createCase(unique + "-p1-first", "P1", null);
        Integer second = createCase(unique + "-p1-second", "P1", null);
        createCase(unique + "-p2-third", "P2", null);

        mockMvc.perform(get("/api/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("priority", "p1")
                        .param("reviewStatus", "pending")
                        .param("executionStatus", "not_run")
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
                .andExpect(jsonPath("$.data.items[*].priority", containsInAnyOrder("P1", "P1")));
    }

    @Test
    void directoryCreateRenameMoveDeleteKeepsTreeShape() throws Exception {
        String unique = uniquePrefix("dir");
        Integer parentId = createDirectory(unique + "-parent", null);
        Integer childId = createDirectory(unique + "-child", parentId);

        mockMvc.perform(get("/api/cases/directories")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].workspaceCode", hasItem(WORKSPACE_CODE)))
                .andExpect(jsonPath("$.data[?(@.workspaceCode=='%s')].children[*].id".formatted(WORKSPACE_CODE), hasItem(parentId)));

        mockMvc.perform(put("/api/cases/directories/{id}", childId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "%s-renamed"
                                }
                                """.formatted(unique)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(childId))
                .andExpect(jsonPath("$.data.name").value(unique + "-renamed"))
                .andExpect(jsonPath("$.data.parentId").value(parentId));

        mockMvc.perform(post("/api/cases/directories/{id}/move", childId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content("""
                                {
                                  "targetParentId": %d
                                }
                                """.formatted(parentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(childId))
                .andExpect(jsonPath("$.data.parentId").value(parentId));

        mockMvc.perform(delete("/api/cases/directories/{id}", parentId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/cases/directories/{id}", childId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/cases/directories/{id}", parentId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/cases/directories")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.workspaceCode=='%s')].children[*].id".formatted(WORKSPACE_CODE), not(hasItem(parentId))))
                .andExpect(jsonPath("$.data[?(@.workspaceCode=='%s')].children[*].id".formatted(WORKSPACE_CODE), not(hasItem(childId))));
    }

    @Test
    void listCasesSupportsDirectoryFilterIncludingDescendants() throws Exception {
        String unique = uniquePrefix("dir-filter");
        Integer parentId = createDirectory(unique + "-parent", null);
        Integer childId = createDirectory(unique + "-child", parentId);
        Integer matched = createCase(unique + "-matched", "P1", childId);
        Integer hidden = createCase(unique + "-hidden", "P1", null);

        mockMvc.perform(get("/api/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("directoryId", parentId.toString())
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(matched)))
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(hidden))));
    }

    private Integer createCase(String title, String priority, Integer directoryId) throws Exception {
        String response = mockMvc.perform(post("/api/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(caseRequest(title, priority, directoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value(title))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asInt();
    }

    private Integer createDirectory(String name, Integer parentId) throws Exception {
        String response = mockMvc.perform(post("/api/cases/directories")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content("""
                                {
                                  "workspaceCode": "%s",
                                  "parentId": %s,
                                  "name": "%s"
                                }
                                """.formatted(WORKSPACE_CODE, parentId == null ? "null" : parentId.toString(), name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value(name))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("id").asInt();
    }

    private String caseRequest(String title, String priority, Integer directoryId) {
        return """
                {
                  "workspaceCode": "%s",
                  "directoryId": %s,
                  "title": "%s",
                  "caseType": "FUNCTION",
                  "priority": "%s",
                  "sourceType": "MANUAL",
                  "caseStatus": "CONFIRMED",
                  "ownerId": 11,
                  "precondition": "logged in",
                  "steps": "1. open page\\n2. submit form",
                  "expectedResult": "form submitted"
                }
                """.formatted(WORKSPACE_CODE, directoryId == null ? "null" : directoryId.toString(), title, priority);
    }

    private String uniquePrefix(String label) {
        return "case-" + label + "-" + System.nanoTime();
    }
}
