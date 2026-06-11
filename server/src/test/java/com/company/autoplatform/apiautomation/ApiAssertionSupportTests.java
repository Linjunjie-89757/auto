package com.company.autoplatform.apiautomation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApiAssertionSupportTests {

    private final ApiAssertionSupport assertionSupport = new ApiAssertionSupport();

    @Test
    void normalizesLegacyResponseAssertionTypeAndCondition() {
        ApiAssertionInput assertion = new ApiAssertionInput(
                "STATUS_CODE",
                "STATUS_CODE",
                "<=",
                "399"
        );

        assertThat(assertionSupport.normalizeAssertionType(assertion)).isEqualTo("RESPONSE_CODE");
        assertThat(assertionSupport.normalizeAssertionCondition(assertion.operator(), "EQUALS")).isEqualTo("LT_OR_EQUALS");
        assertThat(assertionSupport.compareValue("200", assertion.operator(), "399").success()).isTrue();
    }

    @Test
    void keepsVariableFailureMessageFormatAvailableToEvaluators() {
        ApiAssertionSupport.ApiAssertionComparison comparison = assertionSupport.compareValue("", "EQUALS", "expected");

        assertThat(comparison.success()).isFalse();
        assertThat("Variable not found: missingToken. " + comparison.message())
                .contains("Variable not found: missingToken")
                .contains("Expected expected but got");
    }

    @Test
    void keepsScenarioAssertionComparisonsForFailedCountDurationAndStepCount() {
        assertThat(assertionSupport.compareValue("1", "LT_OR_EQUALS", "2").success()).isTrue();
        assertThat(assertionSupport.compareValue("120", "LT", "200").success()).isTrue();
        assertThat(assertionSupport.compareValue("3", "EQUALS", "3").success()).isTrue();
    }

    @Test
    void returnsFirstFailedAssertionMessage() {
        List<ApiAssertionResult> results = List.of(
                new ApiAssertionResult("a1", "SCENARIO", "First", "FAILED_COUNT_LTE", "LT_OR_EQUALS", "0", "1", false, "first failure"),
                new ApiAssertionResult("a2", "SCENARIO", "Second", "STEP_COUNT_EQUALS", "EQUALS", "3", "2", false, "second failure")
        );

        assertThat(assertionSupport.firstFailedMessage(results)).isEqualTo("first failure");
    }
}
