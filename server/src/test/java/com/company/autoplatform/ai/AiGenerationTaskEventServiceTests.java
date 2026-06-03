package com.company.autoplatform.ai;

import com.company.autoplatform.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationTaskEventServiceTests extends IntegrationTestSupport {

    @Autowired
    private AiGenerationTaskEventService eventService;

    @Test
    void appendsEventsWithIncreasingSequenceAndListsInOrder() {
        String taskId = "TASK_STREAM_TEST_" + UUID.randomUUID().toString().replace("-", "");
        AiGenerationTaskEventResponse first = eventService.append(
                taskId,
                "TASK_STARTED",
                "SETUP",
                "INFO",
                "任务已创建",
                null,
                null,
                "openai",
                "gpt-5",
                null
        );
        AiGenerationTaskEventResponse second = eventService.append(
                taskId,
                "CASE_GENERATED",
                "GENERATING",
                "INFO",
                "第 1 条用例已加入列表",
                0,
                "登录成功",
                "openai",
                "gpt-5",
                "{\"title\":\"登录成功\"}"
        );

        List<AiGenerationTaskEventResponse> events = eventService.list(taskId);

        assertThat(first.seq()).isEqualTo(1);
        assertThat(second.seq()).isEqualTo(2);
        assertThat(events).extracting(AiGenerationTaskEventResponse::seq).containsExactly(1, 2);
        assertThat(events).extracting(AiGenerationTaskEventResponse::eventType)
                .containsExactly("TASK_STARTED", "CASE_GENERATED");
    }
}
