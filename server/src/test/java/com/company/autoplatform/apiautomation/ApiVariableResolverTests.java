package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiKeyValueInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiVariableResolverTests {

    private final ApiVariableResolver resolver = new ApiVariableResolver();

    @Test
    void replaceVariablesKeepsMissingVariableFailureBehavior() {
        assertThat(resolver.replaceVariables("Bearer {{ token }}", Map.of("token", "abc")))
                .isEqualTo("Bearer abc");

        assertThatThrownBy(() -> resolver.replaceVariables("Bearer {{missing}}", Map.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Missing variable: missing");
    }

    @Test
    void toEnabledMapFiltersDisabledItemsAndResolvesValuesOnly() {
        Map<String, String> result = resolver.toEnabledMap(List.of(
                item("X-Token", "{{token}}", true, null),
                item("X-Skip", "skip", false, null),
                item("{{rawKey}}", "raw", true, null)
        ), Map.of("token", "abc", "rawKey", "resolved-key"));

        assertThat(result)
                .containsEntry("X-Token", "abc")
                .containsEntry("{{rawKey}}", "raw")
                .doesNotContainKey("X-Skip");
    }

    @Test
    void buildQueryStringResolvesKeysAndValuesAndHonorsEncodeFlag() {
        String query = resolver.buildQueryString(List.of(
                item("plain", "{{value}}", true, false),
                item("{{encodedKey}}", "a b", true, true),
                item("disabled", "nope", false, true)
        ), Map.of("value", "ok", "encodedKey", "hello key"));

        assertThat(query).isEqualTo("plain=ok&hello+key=a+b");
    }

    private ApiKeyValueInput item(String key, String value, Boolean enabled, Boolean encode) {
        return new ApiKeyValueInput(key, value, null, enabled, null, null, encode, null, null, null, null, null);
    }
}
