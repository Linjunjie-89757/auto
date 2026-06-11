package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiAssertionInput;
import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiAssertionResult;

@Component
public class ApiAssertionSupport {

    ApiAssertionComparison compareValue(String actual, String condition, String expectedValue) {
        String normalized = normalizeAssertionCondition(condition, "EQUALS");
        String safeActual = Optional.ofNullable(actual).orElse("");
        String safeExpected = Optional.ofNullable(expectedValue).orElse("");
        boolean success = switch (normalized) {
            case "UNCHECKED" -> true;
            case "EQUALS" -> safeActual.equals(safeExpected);
            case "NOT_EQUALS" -> !safeActual.equals(safeExpected);
            case "CONTAINS" -> safeActual.contains(safeExpected);
            case "NOT_CONTAINS" -> !safeActual.contains(safeExpected);
            case "EMPTY" -> safeActual.isEmpty();
            case "NOT_EMPTY" -> !safeActual.isEmpty();
            case "START_WITH" -> safeActual.startsWith(safeExpected);
            case "END_WITH" -> safeActual.endsWith(safeExpected);
            case "REGEX" -> Pattern.compile(safeExpected, Pattern.DOTALL).matcher(safeActual).find();
            case "GT" -> compareNumber(safeActual, safeExpected) > 0;
            case "GT_OR_EQUALS" -> compareNumber(safeActual, safeExpected) >= 0;
            case "LT" -> compareNumber(safeActual, safeExpected) < 0;
            case "LT_OR_EQUALS" -> compareNumber(safeActual, safeExpected) <= 0;
            case "LENGTH_EQUALS" -> safeActual.length() == parseExpectedLength(safeExpected);
            case "LENGTH_NOT_EQUALS" -> safeActual.length() != parseExpectedLength(safeExpected);
            case "LENGTH_GT" -> safeActual.length() > parseExpectedLength(safeExpected);
            case "LENGTH_GT_OR_EQUALS" -> safeActual.length() >= parseExpectedLength(safeExpected);
            case "LENGTH_LT" -> safeActual.length() < parseExpectedLength(safeExpected);
            case "LENGTH_LT_OR_EQUALS" -> safeActual.length() <= parseExpectedLength(safeExpected);
            default -> throw new BadRequestException("Unsupported assertion condition: " + normalized);
        };
        return comparisonResult(success, safeActual, safeExpected);
    }

    ApiAssertionComparison compareValues(List<String> actualValues, String condition, String expectedValue) {
        List<String> values = actualValues == null ? List.of() : actualValues;
        String normalized = normalizeAssertionCondition(condition, "EQUALS");
        if ("EMPTY".equals(normalized)) {
            boolean success = values.isEmpty() || values.stream().allMatch(value -> value == null || value.isEmpty());
            return comparisonResult(success, formatActualValues(values), expectedValue);
        }
        if ("NOT_EMPTY".equals(normalized)) {
            boolean success = !values.isEmpty() && values.stream().anyMatch(value -> value != null && !value.isEmpty());
            return comparisonResult(success, formatActualValues(values), expectedValue);
        }
        if (values.isEmpty()) {
            return new ApiAssertionComparison(false, "No value matched expression");
        }
        return values.stream()
                .map(value -> compareValue(value, normalized, expectedValue))
                .filter(ApiAssertionComparison::success)
                .findFirst()
                .orElseGet(() -> comparisonResult(false, formatActualValues(values), expectedValue));
    }

    String normalizeAssertionType(ApiAssertionInput assertion) {
        String type = Optional.ofNullable(firstNonBlank(assertion.assertionType(), assertion.type()))
                .orElse("")
                .toUpperCase(Locale.ROOT);
        return switch (type) {
            case "STATUS_CODE" -> "RESPONSE_CODE";
            case "HEADER_EQUALS", "HEADER_CONTAINS" -> "RESPONSE_HEADER";
            case "BODY_JSONPATH_EQUALS", "BODY_JSONPATH_CONTAINS" -> "RESPONSE_BODY";
            case "RESPONSE_TIME_LE" -> "RESPONSE_TIME";
            default -> type;
        };
    }

    String normalizeAssertionCondition(String condition, String fallback) {
        String normalized = Optional.ofNullable(firstNonBlank(condition, fallback))
                .orElse("EQUALS")
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "=", "==", "EQUAL" -> "EQUALS";
            case "!=", "<>", "NOT_EQUAL" -> "NOT_EQUALS";
            case "NOTCONTAINS" -> "NOT_CONTAINS";
            case "STARTS_WITH", "START_WITH" -> "START_WITH";
            case "ENDS_WITH", "END_WITH" -> "END_WITH";
            case "GTE", ">=" -> "GT_OR_EQUALS";
            case "GT", ">" -> "GT";
            case "LTE", "<=" -> "LT_OR_EQUALS";
            case "LT", "<" -> "LT";
            default -> normalized;
        };
    }

    String firstFailedMessage(List<ApiAssertionResult> results) {
        return (results == null ? List.<ApiAssertionResult>of() : results).stream()
                .filter(item -> !item.success())
                .map(ApiAssertionResult::message)
                .findFirst()
                .orElse("Assertion failed");
    }

    private ApiAssertionComparison comparisonResult(boolean success, String actual, String expectedValue) {
        return new ApiAssertionComparison(success, success ? "Assertion passed" : "Expected " + expectedValue + " but got " + actual);
    }

    private int compareNumber(String actual, String expectedValue) {
        try {
            return new BigDecimal(actual.trim()).compareTo(new BigDecimal(expectedValue.trim()));
        } catch (RuntimeException exception) {
            throw new BadRequestException("Actual and expected values must be numeric");
        }
    }

    private int parseExpectedLength(String expectedValue) {
        try {
            return Integer.parseInt(expectedValue.trim());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Expected value must be an integer length");
        }
    }

    private String formatActualValues(List<String> values) {
        return ApiAutomationJsonSupport.toJson(values == null ? List.of() : values, "Failed to serialize assertion actual values");
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    record ApiAssertionComparison(boolean success, String message) {
    }
}
