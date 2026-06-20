package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WebUiExecutionControllerIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private WebUiBrowserRunner browserRunner;

    @Test
    void runCasePersistsSuccessRunAndUpdatesCaseLastRun() throws Exception {
        stubBrowserRunner();
        Long caseId = createCase(uniquePrefix("success"), List.of(
                openStep("Open page", "https://example.com", 1),
                screenshotStep("Capture", 2)
        ));

        String response = mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalSteps").value(2))
                .andExpect(jsonPath("$.data.passedSteps").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long runId = objectMapper.readTree(response).at("/data/runId").asLong();
        mockMvc.perform(get("/api/automation/web/runs/{id}", runId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].status").value("PASSED"))
                .andExpect(jsonPath("$.data.steps[1].status").value("PASSED"));

        mockMvc.perform(get("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastRunResult").value("SUCCESS"));
    }

    @Test
    void failedStepSkipsRemainingStepsWhenContinueOnFailureIsFalse() throws Exception {
        stubBrowserRunner();
        Long caseId = createCase(uniquePrefix("failed"), List.of(
                clickStep("Click missing", "CSS", "#missing", false, 1),
                screenshotStep("Should skip", 2)
        ));

        mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failedSteps").value(1))
                .andExpect(jsonPath("$.data.skippedSteps").value(1))
                .andExpect(jsonPath("$.data.stepResults[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.stepResults[1].status").value("SKIPPED"));

        mockMvc.perform(get("/api/automation/web/cases/{id}", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastRunResult").value("FAILED"));
    }

    @Test
    void runCaseRejectsNoEnabledSteps() throws Exception {
        Long caseId = createCase(uniquePrefix("empty"), List.of(
                disabledOpenStep("Disabled", "https://example.com", 1)
        ));

        mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void runCaseStoresScreenshotArtifactAndDownloadsIt() throws Exception {
        byte[] screenshotBytes = "fake-png-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubBrowserRunnerWithScreenshot(screenshotBytes);
        Long caseId = createCase(uniquePrefix("screenshot"), List.of(
                screenshotStep("Capture", 1)
        ));

        String response = mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stepResults[0].screenshotArtifactId").isNumber())
                .andExpect(jsonPath("$.data.stepResults[0].screenshotUrl").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long runId = objectMapper.readTree(response).at("/data/runId").asLong();
        Long artifactId = objectMapper.readTree(response).at("/data/stepResults/0/screenshotArtifactId").asLong();
        mockMvc.perform(get("/api/automation/web/runs/{runId}/artifacts/{artifactId}/download", runId, artifactId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                        .startsWith("image/png"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(screenshotBytes));
    }

    @Test
    void artifactDownloadRequiresArtifactToBelongToRun() throws Exception {
        mockMvc.perform(get("/api/automation/web/runs/{runId}/artifacts/{artifactId}/download", 999999, 888888)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchRunExecutesCasesWithEnvironmentOverrideAndReturnsBatchDetail() throws Exception {
        stubBrowserRunner();
        Long environmentId = createEnvironment(uniquePrefix("env"), "https://batch.example.test");
        Long firstCaseId = createCase(uniquePrefix("batch-pass"), List.of(
                openStep("Open first", "/first", 1)
        ));
        Long secondCaseId = createCase(uniquePrefix("batch-fail"), List.of(
                clickStep("Click missing", "CSS", "#missing", false, 1)
        ));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("batchName", "Nightly web UI");
        request.put("caseIds", List.of(firstCaseId, secondCaseId));
        request.put("environmentId", environmentId);
        request.put("headless", true);
        request.put("stopOnFailure", false);

        String response = mockMvc.perform(post("/api/automation/web/batches/run")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.totalCases").value(2))
                .andExpect(jsonPath("$.data.successCases").value(1))
                .andExpect(jsonPath("$.data.failedCases").value(1))
                .andExpect(jsonPath("$.data.runs.length()").value(2))
                .andExpect(jsonPath("$.data.runs[0].baseUrl").value("https://batch.example.test"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long batchId = objectMapper.readTree(response).at("/data/batchId").asLong();
        mockMvc.perform(get("/api/automation/web/batches/{id}", batchId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.id").value(batchId))
                .andExpect(jsonPath("$.data.summary.status").value("FAILED"))
                .andExpect(jsonPath("$.data.runs.length()").value(2));
    }

    @Test
    void runCaseUsesPublicWebUiEnvironmentDefaultVariableSetAndStoresMaskedSnapshot() throws Exception {
        AtomicReference<WebUiBrowserRunner.WebUiRunContext> capturedContext = new AtomicReference<>();
        stubBrowserRunner(capturedContext);
        Long variableSetId = createPublicVariableSet(uniquePrefix("vars"));
        String variableSetName = jdbcTemplate.queryForObject(
                "select param_name from tb_param_set where id = ?",
                String.class,
                variableSetId
        );
        Long publicEnvironmentBridgeId = createPublicEnvironment(uniquePrefix("public-env"), variableSetId);
        String environmentsResponse = mockMvc.perform(get("/api/automation/web/environments")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode publicEnvironment = findEnvironment(environmentsResponse, publicEnvironmentBridgeId);
        org.assertj.core.api.Assertions.assertThat(publicEnvironment.path("source").asText()).isEqualTo("CONFIG_CENTER");
        org.assertj.core.api.Assertions.assertThat(publicEnvironment.path("defaultVariableSetId").asLong()).isEqualTo(variableSetId);
        org.assertj.core.api.Assertions.assertThat(publicEnvironment.path("defaultVariableSetName").asText()).isEqualTo(variableSetName);

        Long caseId = createCase(uniquePrefix("public-context"), List.of(
                openStep("Open variable page", "/login?u={{USERNAME}}&literal=\\{{PASSWORD}}", 1),
                fillStep("Fill password", "CSS", "#password", "{{PASSWORD}}", 2)
        ));

        String response = mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("environmentId", publicEnvironmentBridgeId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.stepResults[0].inputValueSnapshot").value("/login?u=alice&literal={{PASSWORD}}"))
                .andExpect(jsonPath("$.data.stepResults[1].inputValueSnapshot").value("s3cr3t"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(capturedContext.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capturedContext.get().baseUrl()).isEqualTo("https://public.example.test");
        org.assertj.core.api.Assertions.assertThat(capturedContext.get().defaultTimeoutMs()).isEqualTo(15000);

        Long runId = objectMapper.readTree(response).at("/data/runId").asLong();
        String snapshotJson = jdbcTemplate.queryForObject(
                "select context_snapshot_json from tb_web_ui_run where id = ?",
                String.class,
                runId
        );
        org.assertj.core.api.Assertions.assertThat(snapshotJson).contains("\"USERNAME\":\"alice\"");
        org.assertj.core.api.Assertions.assertThat(snapshotJson).contains("\"PASSWORD\":\"******\"");
        org.assertj.core.api.Assertions.assertThat(snapshotJson).doesNotContain("s3cr3t");

        mockMvc.perform(get("/api/automation/web/runs/{id}", runId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.context.environment.name").value(publicEnvironment.path("environmentName").asText()))
                .andExpect(jsonPath("$.data.context.environment.baseUrl").value("https://public.example.test"))
                .andExpect(jsonPath("$.data.context.environment.browserType").value("CHROMIUM"))
                .andExpect(jsonPath("$.data.context.variableSetId").value(variableSetId))
                .andExpect(jsonPath("$.data.context.variableSetName").value(variableSetName))
                .andExpect(jsonPath("$.data.context.variables.USERNAME").value("alice"))
                .andExpect(jsonPath("$.data.context.variables.PASSWORD").value("******"));
    }

    @Test
    void ciBatchRunUsesBearerTokenAndReturnsReportUrl() throws Exception {
        stubBrowserRunner();
        String rawToken = "ci-web-ui-token-" + System.nanoTime();
        insertCiToken("risk-ops-ci", rawToken);
        Long environmentId = createEnvironment(uniquePrefix("ci-env"), "https://ci.example.test");
        Long caseId = createCase(uniquePrefix("ci-pass"), List.of(
                openStep("Open ci page", "/ci", 1)
        ));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("batchName", "Jenkins Web UI");
        request.put("caseIds", List.of(caseId));
        request.put("environmentId", environmentId);
        request.put("externalBuildId", "jenkins-42");

        String response = mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalCases").value(1))
                .andExpect(jsonPath("$.data.reportUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.externalBuildId").value("jenkins-42"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long batchId = objectMapper.readTree(response).at("/data/batchId").asLong();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(response).at("/data/reportUrl").asText())
                .isEqualTo("/automation/web?tab=batches&batchId=" + batchId);
    }

    @Test
    void ciBatchRunReturnsCiFriendlyFailureSummary() throws Exception {
        stubBrowserRunner();
        String rawToken = "ci-web-ui-failure-token-" + System.nanoTime();
        insertCiToken("risk-ops-ci-failure", rawToken);
        Long environmentId = createEnvironment(uniquePrefix("ci-failure-env"), "https://ci-failure.example.test");
        Long passingCaseId = createCase(uniquePrefix("ci-pass"), List.of(
                openStep("Open ci passing page", "/ci-pass", 1)
        ));
        Long failingCaseId = createCase(uniquePrefix("ci-fail"), List.of(
                clickStep("Click missing ci element", "CSS", "#missing", false, 1)
        ));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("batchName", "Jenkins Web UI Failure");
        request.put("caseIds", List.of(passingCaseId, failingCaseId));
        request.put("environmentId", environmentId);
        request.put("externalBuildId", "jenkins-43");

        mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.summaryText").value(org.hamcrest.Matchers.containsString("Web UI 批次失败")))
                .andExpect(jsonPath("$.data.summaryText").value(org.hamcrest.Matchers.containsString("1/2 失败")))
                .andExpect(jsonPath("$.data.failedRuns.length()").value(1))
                .andExpect(jsonPath("$.data.failedRuns[0].caseId").value(failingCaseId))
                .andExpect(jsonPath("$.data.failedRuns[0].caseName").isNotEmpty())
                .andExpect(jsonPath("$.data.failedRuns[0].failureSummary").value("Locator not found: #missing"))
                .andExpect(jsonPath("$.data.failedRuns[0].reportUrl").value(org.hamcrest.Matchers.matchesPattern("/automation/web\\?tab=runs&runId=\\d+")));
    }

    @Test
    void ciTokenManagementCreatesListsDisablesAndRotatesToken() throws Exception {
        stubBrowserRunner();
        Long caseId = createCase(uniquePrefix("ci-token-pass"), List.of(
                openStep("Open ci token page", "/ci-token", 1)
        ));

        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("workspaceCode", WORKSPACE_CODE);
        createRequest.put("tokenName", "Jenkins regression");

        String createResponse = mockMvc.perform(post("/api/automation/web/ci/tokens")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long tokenId = objectMapper.readTree(createResponse).at("/data/id").asLong();
        String firstToken = objectMapper.readTree(createResponse).at("/data/token").asText();

        mockMvc.perform(get("/api/automation/web/ci/tokens")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(tokenId))
                .andExpect(jsonPath("$.data.items[0].token").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].tokenName").value("Jenkins regression"));

        Map<String, Object> ciRequest = new LinkedHashMap<>();
        ciRequest.put("workspaceCode", WORKSPACE_CODE);
        ciRequest.put("batchName", "Jenkins token management");
        ciRequest.put("caseIds", List.of(caseId));

        mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ciRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(post("/api/automation/web/ci/tokens/{id}/disable", tokenId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ciRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        String rotateResponse = mockMvc.perform(post("/api/automation/web/ci/tokens/{id}/rotate", tokenId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tokenId))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedToken = objectMapper.readTree(rotateResponse).at("/data/token").asText();

        mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ciRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .header("Authorization", "Bearer " + rotatedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ciRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void ciTokenManagementRequiresLoginButCiTriggerAllowsBearerToken() throws Exception {
        stubBrowserRunner();
        String rawToken = "ci-web-ui-anonymous-token-" + System.nanoTime();
        insertCiToken("anonymous-ci", rawToken);
        Long caseId = createCase(uniquePrefix("anonymous-ci-pass"), List.of(
                openStep("Open anonymous ci page", "/anonymous-ci", 1)
        ));

        mockMvc.perform(get("/api/automation/web/ci/tokens")
                        .with(anonymous())
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isUnauthorized());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("batchName", "Anonymous Jenkins Web UI");
        request.put("caseIds", List.of(caseId));

        mockMvc.perform(post("/api/automation/web/ci/batches/run")
                        .with(anonymous())
                        .header("Authorization", "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void publicRunShareAllowsAnonymousReadOnlyReportAndArtifactDownload() throws Exception {
        byte[] screenshotBytes = "public-run-share-screenshot".getBytes(StandardCharsets.UTF_8);
        stubBrowserRunnerWithScreenshot(screenshotBytes);
        Long caseId = createCase(uniquePrefix("public-run-share"), List.of(
                screenshotStep("Public capture", 1)
        ));

        String runResponse = mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long runId = objectMapper.readTree(runResponse).at("/data/runId").asLong();
        Long artifactId = objectMapper.readTree(runResponse).at("/data/stepResults/0/screenshotArtifactId").asLong();

        String shareResponse = mockMvc.perform(post("/api/automation/web/report-shares")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shareType", "RUN",
                                "targetId", runId,
                                "expiresInDays", 7
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareType").value("RUN"))
                .andExpect(jsonPath("$.data.targetId").value(runId))
                .andExpect(jsonPath("$.data.shareUrl").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(shareResponse).at("/data/token").asText();

        mockMvc.perform(get("/api/public/automation/web/report-shares/{token}", token)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareType").value("RUN"))
                .andExpect(jsonPath("$.data.run.summary.id").value(runId))
                .andExpect(jsonPath("$.data.run.steps[0].screenshotUrl").value(
                        "/api/public/automation/web/report-shares/" + token + "/runs/" + runId
                                + "/artifacts/" + artifactId + "/download"
                ));

        mockMvc.perform(get("/api/public/automation/web/report-shares/{token}/runs/{runId}/artifacts/{artifactId}/download",
                        token,
                        runId,
                        artifactId)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(screenshotBytes));
    }

    @Test
    void publicBatchShareAllowsAnonymousReadOnlyBatchReport() throws Exception {
        stubBrowserRunner();
        Long firstCaseId = createCase(uniquePrefix("public-batch-one"), List.of(
                openStep("Open first public batch page", "/batch-one", 1)
        ));
        Long secondCaseId = createCase(uniquePrefix("public-batch-two"), List.of(
                openStep("Open second public batch page", "/batch-two", 1)
        ));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("batchName", "Public shared batch");
        request.put("caseIds", List.of(firstCaseId, secondCaseId));

        String batchRunResponse = mockMvc.perform(post("/api/automation/web/batches/run")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long batchId = objectMapper.readTree(batchRunResponse).at("/data/batchId").asLong();

        String shareResponse = mockMvc.perform(post("/api/automation/web/report-shares")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shareType", "BATCH",
                                "targetId", batchId,
                                "expiresInDays", 7
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareType").value("BATCH"))
                .andExpect(jsonPath("$.data.targetId").value(batchId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(shareResponse).at("/data/token").asText();

        mockMvc.perform(get("/api/public/automation/web/report-shares/{token}", token)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareType").value("BATCH"))
                .andExpect(jsonPath("$.data.batch.summary.id").value(batchId))
                .andExpect(jsonPath("$.data.batch.runs.length()").value(2))
                .andExpect(jsonPath("$.data.run").doesNotExist());
    }

    @Test
    void revokedOrExpiredPublicShareCannotBeUsedAnonymously() throws Exception {
        stubBrowserRunner();
        Long caseId = createCase(uniquePrefix("public-revoked"), List.of(
                openStep("Open revoked public page", "/revoked", 1)
        ));
        String runResponse = mockMvc.perform(post("/api/automation/web/cases/{id}/run", caseId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long runId = objectMapper.readTree(runResponse).at("/data/runId").asLong();

        String shareResponse = mockMvc.perform(post("/api/automation/web/report-shares")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shareType", "RUN",
                                "targetId", runId,
                                "expiresInDays", 7
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode share = objectMapper.readTree(shareResponse).at("/data");
        Long shareId = share.at("/id").asLong();
        String token = share.at("/token").asText();

        mockMvc.perform(post("/api/automation/web/report-shares/{id}/revoke", shareId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(get("/api/public/automation/web/report-shares/{token}", token)
                        .with(anonymous()))
                .andExpect(status().isNotFound());

        String expiredToken = "expired-share-" + System.nanoTime();
        insertReportShare("RUN", runId, expiredToken, 1, -1);
        mockMvc.perform(get("/api/public/automation/web/report-shares/{token}", expiredToken)
                        .with(anonymous()))
                .andExpect(status().isNotFound());
    }

    private void stubBrowserRunner() {
        stubBrowserRunner(null);
    }

    private void stubBrowserRunner(AtomicReference<WebUiBrowserRunner.WebUiRunContext> capturedContext) {
        Mockito.when(browserRunner.run(any())).thenAnswer(invocation -> {
            WebUiBrowserRunner.WebUiRunContext context = invocation.getArgument(0);
            if (capturedContext != null) {
                capturedContext.set(context);
            }
            return context.steps().stream()
                    .map(step -> {
                        boolean success = !"#missing".equals(step.getLocatorValue());
                        return new WebUiBrowserRunner.StepExecutionResult(
                                step,
                                success,
                                25L,
                                success ? null : "Locator not found: " + step.getLocatorValue(),
                                null
                        );
                    })
                    .toList();
        });
    }

    private void stubBrowserRunnerWithScreenshot(byte[] screenshotBytes) {
        Mockito.when(browserRunner.run(any())).thenAnswer(invocation -> {
            WebUiBrowserRunner.WebUiRunContext context = invocation.getArgument(0);
            return context.steps().stream()
                    .map(step -> new WebUiBrowserRunner.StepExecutionResult(step, true, 25L, null, screenshotBytes))
                    .toList();
        });
    }

    private Long createCase(String caseName, List<Map<String, Object>> steps) throws Exception {
        String response = mockMvc.perform(post("/api/automation/web/cases")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseRequest(caseName, steps))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Long createEnvironment(String environmentName, String baseUrl) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("environmentName", environmentName);
        request.put("baseUrl", baseUrl);
        request.put("browserType", "chromium");
        request.put("headless", true);
        request.put("defaultTimeoutMs", 120000);
        request.put("status", 1);
        String response = mockMvc.perform(post("/api/automation/web/environments")
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

    private Long createPublicEnvironment(String environmentName, Long defaultVariableSetId) {
        Long workspaceId = workspaceId();
        String configJson = """
                {
                  "browserType": "CHROMIUM",
                  "headless": true,
                  "defaultTimeoutMs": 15000,
                  "defaultVariableSetId": %d
                }
                """.formatted(defaultVariableSetId);
        jdbcTemplate.update("""
                        insert into tb_env_config (
                            workspace_id, env_type, env_name, base_url, config_json, status, created_at, updated_at
                        ) values (?, 'WEB_UI', ?, 'https://public.example.test', ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                workspaceId,
                environmentName,
                configJson
        );
        Long publicId = jdbcTemplate.queryForObject(
                "select max(id) from tb_env_config where workspace_id = ? and env_type = 'WEB_UI'",
                Long.class,
                workspaceId
        );
        return -publicId;
    }

    private Long createPublicVariableSet(String variableSetName) {
        Long workspaceId = workspaceId();
        jdbcTemplate.update("""
                        insert into tb_param_set (
                            workspace_id, param_type, param_name, content_json, status, created_at, updated_at
                        ) values (?, 'WEB_UI_VARIABLE_SET', ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                workspaceId,
                variableSetName,
                """
                        [
                          {"name":"USERNAME","value":"alice","sensitive":false,"description":"login user"},
                          {"name":"PASSWORD","value":"s3cr3t","sensitive":true,"description":"login password"}
                        ]
                        """
        );
        return jdbcTemplate.queryForObject(
                "select max(id) from tb_param_set where workspace_id = ? and param_type = 'WEB_UI_VARIABLE_SET'",
                Long.class,
                workspaceId
        );
    }

    private Long workspaceId() {
        return jdbcTemplate.queryForObject(
                "select id from tb_sys_workspace where workspace_code = ?",
                Long.class,
                WORKSPACE_CODE
        );
    }

    private JsonNode findEnvironment(String response, Long environmentId) throws Exception {
        for (JsonNode item : objectMapper.readTree(response).at("/data/items")) {
            if (item.path("id").asLong() == environmentId) {
                return item;
            }
        }
        throw new AssertionError("Environment not found in response: " + environmentId);
    }

    private void insertCiToken(String tokenName, String rawToken) throws Exception {
        Long workspaceId = workspaceId();
        jdbcTemplate.update("""
                        insert into tb_web_ui_ci_token (
                            workspace_id, token_name, token_hash, status, created_by, created_at, updated_at
                        ) values (?, ?, ?, 1, 'Zhang Li', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                workspaceId,
                tokenName,
                sha256(rawToken)
        );
    }

    private void insertReportShare(String shareType, Long targetId, String rawToken, int status, int expiresInDays) throws Exception {
        Long workspaceId = workspaceId();
        jdbcTemplate.update("""
                        insert into tb_web_ui_report_share (
                            workspace_id, share_type, target_id, token_hash, status, expires_at, created_by, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, DATEADD('DAY', ?, CURRENT_TIMESTAMP), 'Zhang Li', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                workspaceId,
                shareType,
                targetId,
                sha256(rawToken),
                status,
                expiresInDays
        );
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> caseRequest(String caseName, List<Map<String, Object>> steps) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workspaceCode", WORKSPACE_CODE);
        request.put("moduleName", "execution");
        request.put("caseName", caseName);
        request.put("description", "web ui execution regression");
        request.put("baseUrl", "https://example.com");
        request.put("browserType", "chromium");
        request.put("headless", true);
        request.put("defaultTimeoutMs", 120000);
        request.put("status", "enabled");
        request.put("steps", steps);
        return request;
    }

    private Map<String, Object> openStep(String name, String url, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", "open",
                "inputValue", url,
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> disabledOpenStep(String name, String url, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", "open",
                "inputValue", url,
                "enabled", false,
                "sortOrder", sortOrder
        );
    }

    private Map<String, Object> clickStep(
            String name,
            String locatorType,
            String locatorValue,
            boolean continueOnFailure,
            int sortOrder
    ) {
        return Map.of(
                "stepName", name,
                "stepType", "click",
                "locatorType", locatorType,
                "locatorValue", locatorValue,
                "continueOnFailure", continueOnFailure,
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

    private Map<String, Object> screenshotStep(String name, int sortOrder) {
        return Map.of(
                "stepName", name,
                "stepType", "screenshot",
                "enabled", true,
                "sortOrder", sortOrder
        );
    }

    private String uniquePrefix(String label) {
        return "web-ui-run-" + label + "-" + System.nanoTime();
    }
}
