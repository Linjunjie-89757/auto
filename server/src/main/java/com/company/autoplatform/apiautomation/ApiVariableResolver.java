package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiKeyValueInput;

@Component
public class ApiVariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    public String replaceVariables(String text, Map<String, String> variables) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!variables.containsKey(key)) {
                throw new BadRequestException("Missing variable: " + key);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Optional.ofNullable(variables.get(key)).orElse("")));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public Map<String, String> toEnabledMap(List<ApiKeyValueInput> items, Map<String, String> variables) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (ApiKeyValueInput item : items) {
            if (item == null || item.key() == null || item.key().isBlank() || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            result.put(item.key(), replaceVariables(Optional.ofNullable(item.value()).orElse(""), variables));
        }
        return result;
    }

    public String buildQueryString(List<ApiKeyValueInput> items, Map<String, String> variables) {
        List<String> parts = new ArrayList<>();
        for (ApiKeyValueInput item : items) {
            if (item == null || item.key() == null || item.key().isBlank() || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            String key = replaceVariables(item.key(), variables);
            String value = replaceVariables(Optional.ofNullable(item.value()).orElse(""), variables);
            if (Boolean.TRUE.equals(item.encode())) {
                key = URLEncoder.encode(key, StandardCharsets.UTF_8);
                value = URLEncoder.encode(value, StandardCharsets.UTF_8);
            }
            parts.add(key + "=" + value);
        }
        return String.join("&", parts);
    }

    public String buildQueryString(Map<String, String> values) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            parts.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(Optional.ofNullable(entry.getValue()).orElse(""), StandardCharsets.UTF_8));
        }
        return String.join("&", parts);
    }
}
