package com.company.autoplatform.ai;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.asset-storage-root=./target/test-ai-assets/ai-case-controller")
class AiCaseControllerIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiProviderClient aiProviderClient;

    @Test
    void providerCreateListUpdateAndSecretKeepResponseShape() throws Exception {
        String unique = uniquePrefix("provider");
        String createdSecret = unique + "-secret";
        String updatedSecret = unique + "-updated-secret";

        Long providerId = createProvider(unique + "-connection", "gpt-5-mini", createdSecret);

        mockMvc.perform(get("/api/cases/ai/providers")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].id", hasItem(providerId.intValue())))
                .andExpect(jsonPath("$.data[*].connectionName", hasItem(unique + "-connection")));

        mockMvc.perform(put("/api/cases/ai/providers/{id}", providerId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(providerRequest(unique + "-connection-updated", "gpt-5.1", updatedSecret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(providerId.intValue()))
                .andExpect(jsonPath("$.data.workspaceCode").value("PERSONAL"))
                .andExpect(jsonPath("$.data.connectionName").value(unique + "-connection-updated"))
                .andExpect(jsonPath("$.data.protocolType").value(AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT))
                .andExpect(jsonPath("$.data.baseUrl").value("https://ai.example.test/v1"))
                .andExpect(jsonPath("$.data.requestTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.data.modelName").value("gpt-5.1"))
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(get("/api/cases/ai/providers/{id}/secret", providerId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(providerId.intValue()))
                .andExpect(jsonPath("$.data.apiKey").value(updatedSecret));
    }

    @Test
    void configCreateGetUpdateAndSecretKeepResponseShape() throws Exception {
        String unique = uniquePrefix("config");
        Long providerId = createProvider(unique + "-provider", "gpt-5-mini", unique + "-provider-secret");

        String createResponse = mockMvc.perform(post("/api/cases/ai/config")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(configRequest(providerId, "CASE_GENERATOR", "gpt-5-mini", unique + " prompt", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.workspaceCode").value("PERSONAL"))
                .andExpect(jsonPath("$.data.roleType").value("CASE_GENERATOR"))
                .andExpect(jsonPath("$.data.providerConnectionId").value(providerId.intValue()))
                .andExpect(jsonPath("$.data.providerConnectionName").value(unique + "-provider"))
                .andExpect(jsonPath("$.data.protocolType").value(AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT))
                .andExpect(jsonPath("$.data.provider").value("OPENAI_COMPATIBLE_CHAT"))
                .andExpect(jsonPath("$.data.model").value("gpt-5-mini"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://ai.example.test/v1"))
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.promptTemplate").value(unique + " prompt"))
                .andExpect(jsonPath("$.data.temperature").value(0.3))
                .andExpect(jsonPath("$.data.topP").value(0.9))
                .andExpect(jsonPath("$.data.maxCases").value(12))
                .andExpect(jsonPath("$.data.supportsImageInput").isBoolean())
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long configId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/cases/ai/config")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generatorConfig.id").value(configId.intValue()))
                .andExpect(jsonPath("$.data.generatorConfig.roleType").value("CASE_GENERATOR"))
                .andExpect(jsonPath("$.data.generatorConfig.providerConnectionId").value(providerId.intValue()))
                .andExpect(jsonPath("$.data.generatorConfig.promptTemplate").value(unique + " prompt"))
                .andExpect(jsonPath("$.data.hasLegacyConfig").isBoolean())
                .andExpect(jsonPath("$.data.canBootstrapFromLegacy").isBoolean());

        mockMvc.perform(put("/api/cases/ai/config/{id}", configId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(configRequest(providerId, "CASE_GENERATOR", "gpt-5.1", unique + " prompt updated", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(configId.intValue()))
                .andExpect(jsonPath("$.data.roleType").value("CASE_GENERATOR"))
                .andExpect(jsonPath("$.data.model").value("gpt-5.1"))
                .andExpect(jsonPath("$.data.promptTemplate").value(unique + " prompt updated"))
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(get("/api/cases/ai/config/{id}/secret", configId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(configId.intValue()))
                .andExpect(jsonPath("$.data.roleType").value("CASE_GENERATOR"))
                .andExpect(jsonPath("$.data.apiKey").value(unique + "-provider-secret"));
    }

    @Test
    void txtRequirementDocumentImportKeepsResponseShape() throws Exception {
        String unique = uniquePrefix("requirement");
        byte[] content = ("# " + unique + " title\n\n- login with valid account\n- verify dashboard").getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", unique + ".txt", "text/plain", content);

        mockMvc.perform(multipart("/api/cases/ai/requirement-import")
                        .file(file)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value(unique + ".txt"))
                .andExpect(jsonPath("$.data.title").value(unique + " title"))
                .andExpect(jsonPath("$.data.content").value(new String(content, StandardCharsets.UTF_8).trim()))
                .andExpect(jsonPath("$.data.charCount").value(new String(content, StandardCharsets.UTF_8).trim().length()))
                .andExpect(jsonPath("$.data.assets.length()").value(0));
    }

    @Test
    void imageAssetUploadDownloadAndDeleteKeepMainFlow() throws Exception {
        String unique = uniquePrefix("asset");
        byte[] png = tinyPng();
        MockMultipartFile file = new MockMultipartFile("files", unique + ".png", "image/png", png);

        String uploadResponse = mockMvc.perform(multipart("/api/cases/ai/assets")
                        .file(file)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].sourceType").value("MANUAL_UPLOAD"))
                .andExpect(jsonPath("$.data[0].fileName").value(unique + ".png"))
                .andExpect(jsonPath("$.data[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.data[0].fileSize").value(png.length))
                .andExpect(jsonPath("$.data[0].extractedText").doesNotExist())
                .andExpect(jsonPath("$.data[0].downloadUrl", notNullValue()))
                .andExpect(jsonPath("$.data[0].createdAt", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long assetId = objectMapper.readTree(uploadResponse).path("data").get(0).path("id").asLong();

        mockMvc.perform(get("/api/cases/ai/assets/{id}/download", assetId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().longValue("Content-Length", png.length))
                .andExpect(content().bytes(png));

        mockMvc.perform(delete("/api/cases/ai/assets/{id}", assetId)
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private Long createProvider(String connectionName, String modelName, String apiKey) throws Exception {
        String response = mockMvc.perform(post("/api/cases/ai/providers")
                        .header(WorkspaceScope.HEADER, WORKSPACE_CODE)
                        .contentType("application/json")
                        .content(providerRequest(connectionName, modelName, apiKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.workspaceCode").value("PERSONAL"))
                .andExpect(jsonPath("$.data.connectionName").value(connectionName))
                .andExpect(jsonPath("$.data.protocolType").value(AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT))
                .andExpect(jsonPath("$.data.baseUrl").value("https://ai.example.test/v1"))
                .andExpect(jsonPath("$.data.modelName").value(modelName))
                .andExpect(jsonPath("$.data.apiKeyMasked").isString())
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.status").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private String providerRequest(String connectionName, String modelName, String apiKey) {
        return """
                {
                  "workspaceCode": "%s",
                  "connectionName": "%s",
                  "protocolType": "%s",
                  "baseUrl": "https://ai.example.test/v1",
                  "requestTimeoutSeconds": 30,
                  "modelName": "%s",
                  "apiKey": "%s",
                  "status": 1
                }
                """.formatted(
                WORKSPACE_CODE,
                connectionName,
                AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT,
                modelName,
                apiKey
        );
    }

    private String configRequest(Long providerId, String roleType, String model, String promptTemplate, int status) {
        return """
                {
                  "workspaceCode": "%s",
                  "roleType": "%s",
                  "providerConnectionId": %d,
                  "model": "%s",
                  "promptTemplate": "%s",
                  "reviewChecklist": "review checklist",
                  "temperature": 0.3,
                  "topP": 0.9,
                  "maxCases": 12,
                  "status": %d
                }
                """.formatted(WORKSPACE_CODE, roleType, providerId, model, promptTemplate, status);
    }

    private byte[] tinyPng() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
    }

    private String uniquePrefix(String label) {
        return "ai-" + label + "-" + System.nanoTime();
    }
}
