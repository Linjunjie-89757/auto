package com.company.autoplatform.webuiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebUiExecutionEngineSupport {

    private static final Pattern COUNT_EXPECTATION_PATTERN = Pattern.compile("^(=|>|<|>=|<=)?\\s*(\\d+)$");

    static String resolveOpenUrl(String inputValue, String baseUrl) {
        String input = WebUiAutomationFormatSupport.blankToNull(inputValue);
        if (input == null) {
            throw new BadRequestException("OPEN step URL cannot be blank");
        }
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        }
        String base = WebUiAutomationFormatSupport.blankToNull(baseUrl);
        if (base == null) {
            throw new BadRequestException("Base URL is required for relative OPEN step URL");
        }
        try {
            return URI.create(base).resolve(input).toString();
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("OPEN step URL is invalid");
        }
    }

    static AttributeExpectation parseAttributeExpectation(String inputValue) {
        String input = WebUiAutomationFormatSupport.blankToNull(inputValue);
        if (input == null) {
            throw new BadRequestException("ASSERT_ATTRIBUTE input must use attribute=value format");
        }
        int separatorIndex = input.indexOf('=');
        if (separatorIndex <= 0 || separatorIndex == input.length() - 1) {
            throw new BadRequestException("ASSERT_ATTRIBUTE input must use attribute=value format");
        }
        String name = WebUiAutomationFormatSupport.blankToNull(input.substring(0, separatorIndex));
        String expectedValue = WebUiAutomationFormatSupport.blankToNull(input.substring(separatorIndex + 1));
        if (name == null || expectedValue == null) {
            throw new BadRequestException("ASSERT_ATTRIBUTE input must use attribute=value format");
        }
        return new AttributeExpectation(name, expectedValue);
    }

    static boolean matchesCountExpectation(int actual, String inputValue) {
        String input = WebUiAutomationFormatSupport.blankToNull(inputValue);
        if (input == null) {
            throw new BadRequestException("ASSERT_COUNT input must use expressions like =1, >0 or <3");
        }
        Matcher matcher = COUNT_EXPECTATION_PATTERN.matcher(input.replace(" ", ""));
        if (!matcher.matches()) {
            throw new BadRequestException("ASSERT_COUNT input must use expressions like =1, >0 or <3");
        }
        String operator = matcher.group(1);
        int expected = Integer.parseInt(matcher.group(2));
        return switch (operator == null ? "=" : operator.toUpperCase(Locale.ROOT)) {
            case "=" -> actual == expected;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            default -> false;
        };
    }

    record AttributeExpectation(String name, String expectedValue) {
    }
}
