package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
class WebUiAutomationControllerIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WebUiLocatorValidationRunner locatorValidationRunner;

    @Test
    void createUpdateListAndDeleteCaseWithSteps() throws Exception {
        String unique = uniquePrefix("case");
        Long caseId = createCase(unique + "-case", "checkout", List.of(
                openStep("Open home", "https://example.com/home", 2),
                clickStep("Click login", "CSS", "#login", 1)
        ));

        mockMvc.perform(get("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(caseId.intValue()))
                .andExpect(jsonPath("$.data.caseName").value(unique + "-case"))
                .andExpect(jsonPath("$.data.browserType").value("CHROMIUM"))
                .andExpect(jsonPath("$.data.defaultTimeoutMs").value(60000))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].stepName").value("Click login"))
                .andExpect(jsonPath("$.data.steps[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.steps[0].timeoutMs").value(5000))
                .andExpect(jsonPath("$.data.steps[0].screenshotPolicy").value("ON_FAILURE"));

        mockMvc.perform(put("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest(unique + "-case-updated", "payments", List.of(
                                fillStep("Fill user", "CSS", "#user", "alice", 1)
                        )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.caseName").value(unique + "-case-updated"))
                .andExpect(jsonPath("$.data.moduleName").value("payments"))
                .andExpect(jsonPath("$.data.steps.length()").value(1))
                .andExpect(jsonPath("$.data.steps[0].stepType").value("FILL"))
                .andExpect(jsonPath("$.data.steps[0].inputValue").value("alice"));

        mockMvc.perform(get("/api/automation/web/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("moduleName", "payments")
                        .param("status", "enabled")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(caseId.intValue()))
                .andExpect(jsonPath("$.data.items[0].caseName").value(unique + "-case-updated"))
                .andExpect(jsonPath("$.data.items[0].moduleName").value("payments"))
                .andExpect(jsonPath("$.data.items[0].stepCount").value(1));

        mockMvc.perform(delete("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCaseAllowsMissingBaseUrl() throws Exception {
        String unique = uniquePrefix("case-no-base-url");

        String response = mockMvc.perform(post("/api/automation/web/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest(unique + "-case", "checkout", List.of(
                                clickStep("Click login", "CSS", "#login", 1)
                        ), false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.caseName").value(unique + "-case"))
                .andExpect(jsonPath("$.data.baseUrl").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long caseId = objectMapper.readTree(response).at("/data/id").asLong();
        mockMvc.perform(get("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseUrl").doesNotExist());
    }

    @Test
    void createCaseAcceptsHighFrequencyStepTypes() throws Exception {
        String unique = uniquePrefix("high-frequency-steps");

        mockMvc.perform(post("/api/automation/web/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest(unique + "-case", "checkout", List.of(
                                locatorOnlyStep("Hover menu", "hover", "#menu", 1),
                                locatorOnlyStep("Double click row", "double_click", ".row", 2),
                                locatorOnlyStep("Right click row", "right_click", ".row", 3),
                                inputOnlyStep("Press enter", "press_key", "Enter", 4),
                                locatorInputStep("Select status", "select", "#status", "APPROVED", 5),
                                locatorInputStep("Upload file", "file_upload", "input[type=file]", "D:/tmp/sample.txt", 6),
                                inputOnlyStep("Assert URL", "assert_url", "/orders", 7),
                                inputOnlyStep("Assert title", "assert_title", "Orders", 8),
                                locatorInputStep("Assert attribute", "assert_attribute", "#submit", "aria-label=Submit", 9),
                                locatorInputStep("Assert count", "assert_count", ".order-row", ">0", 10)
                        )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.steps.length()").value(10))
                .andExpect(jsonPath("$.data.steps[0].stepType").value("HOVER"))
                .andExpect(jsonPath("$.data.steps[1].stepType").value("DOUBLE_CLICK"))
                .andExpect(jsonPath("$.data.steps[2].stepType").value("RIGHT_CLICK"))
                .andExpect(jsonPath("$.data.steps[3].stepType").value("PRESS_KEY"))
                .andExpect(jsonPath("$.data.steps[4].stepType").value("SELECT"))
                .andExpect(jsonPath("$.data.steps[5].stepType").value("FILE_UPLOAD"))
                .andExpect(jsonPath("$.data.steps[6].stepType").value("ASSERT_URL"))
                .andExpect(jsonPath("$.data.steps[7].stepType").value("ASSERT_TITLE"))
                .andExpect(jsonPath("$.data.steps[8].stepType").value("ASSERT_ATTRIBUTE"))
                .andExpect(jsonPath("$.data.steps[9].stepType").value("ASSERT_COUNT"));
    }

    @Test
    void createListSaveFromCaseAndDeleteTemplate() throws Exception {
        String unique = uniquePrefix("template");
        Long caseId = createCase(unique + "-case", "template-source", List.of(
                openStep("Open template source", "/source", 1),
                fillStep("Fill keyword", "CSS", "#keyword", "codex", 2)
        ));

        String templateResponse = mockMvc.perform(post("/api/automation/web/templates")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(templateRequest(unique + "-manual", "template-library", List.of(
                                openStep("Open manual template", "/manual", 1)
                        )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateName").value(unique + "-manual"))
                .andExpect(jsonPath("$.data.stepCount").value(1))
                .andExpect(jsonPath("$.data.steps[0].stepType").value("OPEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long templateId = objectMapper.readTree(templateResponse).at("/data/id").asLong();

        mockMvc.perform(get("/api/automation/web/templates")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(templateId.intValue()))
                .andExpect(jsonPath("$.data.items[0].templateName").value(unique + "-manual"));

        String savedTemplateResponse = mockMvc.perform(post("/api/automation/web/cases/{id}/save-as-template", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "templateName", unique + "-from-case",
                                "description", "saved from case"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateName").value(unique + "-from-case"))
                .andExpect(jsonPath("$.data.moduleName").value("template-source"))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[1].inputValue").value("codex"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long savedTemplateId = objectMapper.readTree(savedTemplateResponse).at("/data/id").asLong();

        mockMvc.perform(delete("/api/automation/web/templates/{id}", templateId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/automation/web/templates")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(savedTemplateId.intValue()))
                .andExpect(jsonPath("$.data.items[0].templateName").value(unique + "-from-case"));
    }

    @Test
    void createUpdateListAndDeleteEnvironment() throws Exception {
        String unique = uniquePrefix("env");
        Long firstId = createEnvironment(unique + "-local", "https://local.example");
        Long secondId = createEnvironment(unique + "-staging", "https://staging.example");

        mockMvc.perform(put("/api/automation/web/environments/{id}", firstId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(environmentRequest(
                                unique + "-local-updated",
                                "https://local-updated.example",
                                "webkit",
                                false,
                                999999,
                                0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(firstId.intValue()))
                .andExpect(jsonPath("$.data.environmentName").value(unique + "-local-updated"))
                .andExpect(jsonPath("$.data.browserType").value("WEBKIT"))
                .andExpect(jsonPath("$.data.defaultTimeoutMs").value(60000))
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(get("/api/automation/web/environments")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[*].id", containsInAnyOrder(firstId.intValue(), secondId.intValue())))
                .andExpect(jsonPath("$.data.items[*].environmentName", hasItem(unique + "-local-updated")))
                .andExpect(jsonPath("$.data.items[*].status", containsInAnyOrder(0, 1)));

        mockMvc.perform(delete("/api/automation/web/environments/{id}", firstId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/automation/web/environments")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(firstId.intValue()))))
                .andExpect(jsonPath("$.data.items[*].id", hasItem(secondId.intValue())));
    }

    @Test
    void listRunsReturnsEmptyPageBeforeAnyExecution() throws Exception {
        mockMvc.perform(get("/api/automation/web/runs")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void clickStepRequiresLocator() throws Exception {
        String unique = uniquePrefix("invalid");

        mockMvc.perform(post("/api/automation/web/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest(unique + "-case", "invalid", List.of(
                                Map.of(
                                        "stepName", "Click without locator",
                                        "stepType", "CLICK",
                                        "sortOrder", 1
                                )
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void validateLocatorReturnsMatchCountAndScreenshot() throws Exception {
        when(locatorValidationRunner.validate(any())).thenReturn(new WebUiLocatorValidationRunner.LocatorValidationResult(
                true,
                2,
                null,
                "fake-png".getBytes()
        ));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("baseUrl", "https://example.com/login");
        request.put("browserType", "chromium");
        request.put("headless", true);
        request.put("locatorType", "CSS");
        request.put("locatorValue", ".login-button");
        request.put("timeoutMs", 3000);

        mockMvc.perform(post("/api/automation/web/locators/validate")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.matchCount").value(2))
                .andExpect(jsonPath("$.data.screenshotBase64").isNotEmpty());
    }

    @Test
    void batchUpdateAndInspectElementQuality() throws Exception {
        String unique = uniquePrefix("element-batch");
        Long moduleId = createElementModule(unique + "-module");
        Long pageId = createElementPage(moduleId, unique + "-page");
        Long firstGroupId = createElementGroup(pageId, unique + "-search");
        Long secondGroupId = createElementGroup(pageId, unique + "-result");
        Long buttonId = createElement(pageId, firstGroupId, unique + "-submit", "CSS", ".el-button");
        Long inputId = createElement(pageId, null, unique + "-keyword", "CSS", "#keyword");

        mockMvc.perform(post("/api/automation/web/elements/batch/status")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "elementIds", List.of(buttonId, inputId),
                                "status", "disabled"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.updatedCount").value(2));

        mockMvc.perform(post("/api/automation/web/elements/batch/move")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "elementIds", List.of(buttonId),
                                "pageId", pageId,
                                "groupId", secondGroupId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mockMvc.perform(get("/api/automation/web/elements")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("status", "disabled")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[?(@.id==" + buttonId + ")].groupName").value(hasItem(unique + "-result")));

        mockMvc.perform(get("/api/automation/web/elements/quality-check")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("pageNo", "1")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.issues[?(@.elementId==" + buttonId + ")].title").value(hasItem("定位器过宽")))
                .andExpect(jsonPath("$.data.issues[?(@.elementId==" + inputId + ")].title").value(hasItem("未归入分组")));
    }

    @Test
    void batchValidateElementsReturnsPerElementResultsAndUpdatesSnapshots() throws Exception {
        when(locatorValidationRunner.validate(any()))
                .thenReturn(new WebUiLocatorValidationRunner.LocatorValidationResult(true, 1, null, "ok-png".getBytes()))
                .thenReturn(new WebUiLocatorValidationRunner.LocatorValidationResult(false, 0, "not found", "fail-png".getBytes()));

        String unique = uniquePrefix("element-validate");
        Long moduleId = createElementModule(unique + "-module");
        Long pageId = createElementPage(moduleId, unique + "-page");
        Long groupId = createElementGroup(pageId, unique + "-form");
        Long usernameId = createElement(pageId, groupId, unique + "-username", "CSS", "#username");
        Long passwordId = createElement(pageId, groupId, unique + "-password", "CSS", "#password");

        mockMvc.perform(post("/api/automation/web/elements/batch/validate")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "elementIds", List.of(usernameId, passwordId),
                                "baseUrl", "https://example.com/login",
                                "browserType", "chromium",
                                "headless", true,
                                "timeoutMs", 3000
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.passedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.results[?(@.elementId==" + usernameId + ")].matched").value(hasItem(true)))
                .andExpect(jsonPath("$.data.results[?(@.elementId==" + passwordId + ")].errorMessage").value(hasItem("not found")));

        mockMvc.perform(get("/api/automation/web/elements")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .param("keyword", unique)
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + usernameId + ")].lastValidateResult").value(hasItem("PASSED")))
                .andExpect(jsonPath("$.data.items[?(@.id==" + passwordId + ")].lastValidateResult").value(hasItem("FAILED")));
    }

    private Long createCase(String caseName, String moduleName, List<Map<String, Object>> steps) throws Exception {
        String response = mockMvc.perform(post("/api/automation/web/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest(caseName, moduleName, steps))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.caseName").value(caseName))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Long createElementModule(String moduleName) throws Exception {
        String response = mockMvc.perform(post("/api/automation/web/elements/modules")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceCode", WORKSPACE_CODE,
                                "moduleName", moduleName,
                                "status", "enabled"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Long createElementPage(Long moduleId, String pageName) throws Exception {
        String response = mockMvc.perform(post("/api/automation/web/elements/pages")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceCode", WORKSPACE_CODE,
                                "moduleId", moduleId,
                                "pageName", pageName,
                                "status", "enabled"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Long createElementGroup(Long pageId, String groupName) throws Exception {
        String response = mockMvc.perform(post("/api/automation/web/elements/groups")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workspaceCode", WORKSPACE_CODE,
                                "pageId", pageId,
                                "groupName", groupName,
                                "status", "enabled"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Long createElement(Long pageId, Long groupId, String elementName, String locatorType, String locatorValue) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("pageId", pageId);
        request.put("groupId", groupId);
        request.put("pageName", elementName + "-page-snapshot");
        request.put("elementName", elementName);
        request.put("locatorType", locatorType);
        request.put("locatorValue", locatorValue);
        request.put("status", "enabled");

        String response = mockMvc.perform(post("/api/automation/web/elements")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Long createEnvironment(String name, String baseUrl) throws Exception {
        String response = mockMvc.perform(post("/api/automation/web/environments")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(environmentRequest(
                                name,
                                baseUrl,
                                null,
                                true,
                                500,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Map<String, Object> caseRequest(String caseName, String moduleName, List<Map<String, Object>> steps) {
        return caseRequest(caseName, moduleName, steps, true);
    }

    private Map<String, Object> caseRequest(
            String caseName,
            String moduleName,
            List<Map<String, Object>> steps,
            boolean includeBaseUrl
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("moduleName", moduleName);
        request.put("caseName", caseName);
        request.put("description", "web ui asset regression");
        if (includeBaseUrl) {
            request.put("baseUrl", "https://example.com");
        }
        request.put("browserType", "chromium");
        request.put("headless", true);
        request.put("defaultTimeoutMs", 120000);
        request.put("status", "enabled");
        request.put("steps", steps);
        return request;
    }

    private Map<String, Object> templateRequest(String templateName, String moduleName, List<Map<String, Object>> steps) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("moduleName", moduleName);
        request.put("templateName", templateName);
        request.put("description", "web ui template regression");
        request.put("baseUrl", "https://template.example");
        request.put("browserType", "chromium");
        request.put("headless", true);
        request.put("defaultTimeoutMs", 120000);
        request.put("status", "enabled");
        request.put("steps", steps);
        return request;
    }

    private Map<String, Object> environmentRequest(
            String name,
            String baseUrl,
            String browserType,
            Boolean headless,
            Integer defaultTimeoutMs,
            Integer status
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("environmentName", name);
        request.put("baseUrl", baseUrl);
        request.put("browserType", browserType == null ? "" : browserType);
        request.put("headless", headless);
        request.put("defaultTimeoutMs", defaultTimeoutMs);
        if (status != null) {
            request.put("status", status);
        }
        return request;
    }

    private Map<String, Object> openStep(String name, String url, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", "open",
                "inputValue", url,
                "timeoutMs", 500,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> clickStep(String name, String locatorType, String locatorValue, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", "click",
                "locatorType", locatorType,
                "locatorValue", locatorValue,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> fillStep(String name, String locatorType, String locatorValue, String inputValue, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", "fill",
                "locatorType", locatorType,
                "locatorValue", locatorValue,
                "inputValue", inputValue,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> locatorOnlyStep(String name, String stepType, String locatorValue, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", stepType,
                "locatorType", "CSS",
                "locatorValue", locatorValue,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> inputOnlyStep(String name, String stepType, String inputValue, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", stepType,
                "inputValue", inputValue,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> locatorInputStep(String name, String stepType, String locatorValue, String inputValue, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", stepType,
                "locatorType", "CSS",
                "locatorValue", locatorValue,
                "inputValue", inputValue,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private String uniquePrefix(String label) {
        return "web-ui-" + label + "-" + System.nanoTime();
    }
}
