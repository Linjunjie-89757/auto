package com.company.autoplatform.ai;

import com.company.autoplatform.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class AiGenerationTaskExecutionServiceTests extends IntegrationTestSupport {

    @Autowired
    private AiCaseService aiCaseService;

    @Autowired
    private AiGenerationTaskService aiGenerationTaskService;

    @MockitoBean
    private AiProviderClient aiProviderClient;

    @Test
    void executeCompleteTaskPersistsGenerationReviewAndEvents() {
        reset(aiProviderClient);
        String unique = uniquePrefix("complete");
        String model = unique + "-model";
        AiProviderConnectionItem provider = aiCaseService.createProvider(WORKSPACE_CODE, new SaveAiProviderConnectionRequest(
                WORKSPACE_CODE,
                unique + "-provider",
                AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT,
                "https://ai.example.test/v1",
                30,
                model,
                unique + "-secret",
                1
        ));
        upsertConfig("CASE_GENERATOR", provider.id(), model, unique + " generator prompt");
        upsertConfig("CASE_REVIEWER", provider.id(), model, unique + " reviewer prompt");

        GeneratedAiCaseItem generatedCase = generatedCase(unique + " generated case");
        when(aiProviderClient.generate(any(), any(), any(), any())).thenReturn(new AiGeneratedCasesResult(
                List.of(generatedCase),
                "coverage summary",
                List.of("remaining gap"),
                List.of("generation warning"),
                List.of(),
                "{\"cases\":[{\"title\":\"" + unique + " generated case\"}]}"
        ));
        when(aiProviderClient.review(any(), any(), any())).thenReturn(new AiReviewResult(
                "APPROVE",
                "review summary",
                List.of("issue one"),
                List.of("suggestion one"),
                List.of(new AiReviewCaseDecision(
                        0,
                        "APPROVED",
                        "case approved",
                        "coverage ok",
                        "evidence ok",
                        "review comment",
                        null,
                        null,
                        null
                )),
                List.of(),
                List.of("unresolved gap"),
                "{\"result\":\"APPROVE\"}",
                true
        ));

        AiGenerationTaskResponse created = aiGenerationTaskService.createTask(WORKSPACE_CODE, new CreateAiGenerationTaskRequest(
                WORKSPACE_CODE,
                unique + " requirement",
                "User can login and view dashboard.",
                "COMPLETE",
                null,
                unique + " directory",
                List.of(),
                0
        ));

        aiGenerationTaskService.executeTask(created.taskId(), WORKSPACE_CODE);

        AiGenerationTaskResponse detail = aiGenerationTaskService.getTask(created.taskId(), WORKSPACE_CODE);
        assertThat(detail.status()).isEqualTo("COMPLETED");
        assertThat(detail.currentStep()).isEqualTo(4);
        assertThat(detail.finishedAt()).isNotBlank();
        assertThat(detail.provider()).isEqualTo("OPENAI_COMPATIBLE_CHAT");
        assertThat(detail.model()).isEqualTo(model);
        assertThat(detail.generatedCount()).isEqualTo(1);
        assertThat(detail.generatedCases()).hasSize(1);
        assertThat(detail.generatedCases().get(0).title()).isEqualTo(unique + " generated case");
        assertThat(detail.generatedCases().get(0).aiReviewStatus()).isEqualTo("APPROVED");
        assertThat(detail.generatedCases().get(0).aiReviewSummary()).isEqualTo("case approved");
        assertThat(detail.reviewResult()).isNotNull();
        assertThat(detail.reviewResult().result()).isEqualTo("APPROVE");
        assertThat(detail.reviewResult().summary()).isEqualTo("review summary");
        assertThat(detail.generationRawOutput()).contains(unique + " generated case");
        assertThat(detail.reviewRawOutput()).isEqualTo("{\"result\":\"APPROVE\"}");
        assertThat(detail.events()).extracting(AiGenerationTaskEventResponse::eventType)
                .contains(
                        "TASK_STARTED",
                        "GENERATION_COMPLETED",
                        "REVIEW_STARTED",
                        "REVIEW_COMPLETED",
                        "FINAL_CASES_READY",
                        "TASK_COMPLETED"
                );
    }

    private void upsertConfig(String roleType, Long providerId, String model, String promptTemplate) {
        SaveAiCaseConfigRequest request = new SaveAiCaseConfigRequest(
                WORKSPACE_CODE,
                roleType,
                providerId,
                null,
                null,
                model,
                null,
                null,
                promptTemplate,
                "review checklist",
                0.3,
                0.9,
                12,
                null,
                true,
                1
        );
        AiCaseConfigResponse current = aiCaseService.getConfig(WORKSPACE_CODE, null);
        AiCaseConfigItem existing = "CASE_REVIEWER".equals(roleType)
                ? current.reviewerConfig()
                : current.generatorConfig();
        if (existing == null) {
            aiCaseService.createConfig(WORKSPACE_CODE, request);
        } else {
            aiCaseService.updateConfig(existing.id(), WORKSPACE_CODE, request);
        }
    }

    private GeneratedAiCaseItem generatedCase(String title) {
        return new GeneratedAiCaseItem(
                title,
                "FUNCTION",
                "P1",
                "User has valid account",
                "1. Open login page",
                "Dashboard is visible",
                "Login risk",
                "Happy path",
                "Core login flow",
                "Requirement line 1",
                "AI_GENERATED",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "PENDING_REVIEW",
                "Pending review",
                false,
                null,
                null
        );
    }

    private String uniquePrefix(String label) {
        return "ai-task-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
