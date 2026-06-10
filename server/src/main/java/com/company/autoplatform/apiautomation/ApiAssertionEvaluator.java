package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;

@Component
public class ApiAssertionEvaluator {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ApiAutomationScriptRunner scriptRunner;

    public ApiAssertionEvaluator(ApiAutomationScriptRunner scriptRunner) {
        this.scriptRunner = scriptRunner;
    }

    public List<ApiAssertionResult> evaluate(
            List<ApiAssertionInput> assertions,
            ApiRequestSnapshot request,
            ApiResponseSnapshot response,
            long durationMs,
            Map<String, String> variables
    ) {
        List<ApiAssertionResult> results = new ArrayList<>();
        for (ApiAssertionInput assertion : defaultList(assertions)) {
            if (assertion == null || Boolean.FALSE.equals(assertion.enabled())) {
                continue;
            }
            String type = normalizeAssertionType(assertion);
            try {
                switch (type) {
                    case "RESPONSE_CODE" -> results.add(evaluateSingleAssertion(
                            assertion,
                            type,
                            firstNonBlank(assertion.name(), "Status Code"),
                            "statusCode",
                            normalizeAssertionCondition(assertion.condition(), legacyCondition(assertion.type(), assertion.operator())),
                            String.valueOf(response.statusCode()),
                            assertion.expectedValue(),
                            variables,
                            null
                    ));
                    case "RESPONSE_HEADER" -> results.addAll(evaluateHeaderAssertions(assertion, response, variables));
                    case "RESPONSE_BODY" -> results.addAll(evaluateBodyAssertions(assertion, response, variables));
                    case "RESPONSE_TIME" -> results.add(evaluateSingleAssertion(
                            assertion,
                            type,
                            firstNonBlank(assertion.name(), "Response Time"),
                            "durationMs",
                            normalizeAssertionCondition(assertion.condition(), "LT_OR_EQUALS"),
                            String.valueOf(durationMs),
                            assertion.expectedValue(),
                            variables,
                            null
                    ));
                    case "VARIABLE" -> results.addAll(evaluateVariableAssertions(assertion, variables));
                    case "SCRIPT" -> results.add(evaluateScriptAssertion(assertion, request, response, variables));
                    default -> throw new BadRequestException("Unsupported assertion type: " + type);
                }
            } catch (Exception exception) {
                results.add(new ApiAssertionResult(
                        assertion.id(),
                        type,
                        firstNonBlank(assertion.name(), defaultAssertionName(type)),
                        assertion.subject(),
                        normalizeAssertionCondition(assertion.condition(), legacyCondition(assertion.type(), assertion.operator())),
                        assertion.expectedValue(),
                        null,
                        false,
                        exception.getMessage()
                ));
            }
        }
        return results;
    }

    private List<ApiAssertionResult> evaluateHeaderAssertions(
            ApiAssertionInput assertion,
            ApiResponseSnapshot response,
            Map<String, String> variables
    ) {
        List<ApiAssertionItemInput> items = defaultList(assertion.assertions());
        if (items.isEmpty() && assertion.subject() != null) {
            items = List.of(new ApiAssertionItemInput(assertion.subject(), null, null,
                    legacyCondition(assertion.type(), assertion.operator()), assertion.expectedValue(), true));
        }
        List<ApiAssertionResult> results = new ArrayList<>();
        int index = 0;
        for (ApiAssertionItemInput item : items) {
            if (item == null || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            String header = replaceVariables(Optional.ofNullable(item.header()).orElse(""), variables);
            String actual = findHeaderValue(response.headers(), header);
            results.add(evaluateSingleAssertion(
                    assertion,
                    "RESPONSE_HEADER",
                    firstNonBlank(assertion.name(), "Response Header"),
                    header,
                    normalizeAssertionCondition(item.condition(), legacyCondition(assertion.type(), assertion.operator())),
                    actual,
                    item.expectedValue(),
                    variables,
                    "header[" + index + "]"
            ));
            index++;
        }
        return results;
    }

    private List<ApiAssertionResult> evaluateBodyAssertions(
            ApiAssertionInput assertion,
            ApiResponseSnapshot response,
            Map<String, String> variables
    ) {
        String bodyType = normalizeBodyAssertionType(assertion);
        ApiAssertionGroupInput group = switch (bodyType) {
            case "X_PATH" -> assertion.xpathAssertion();
            case "REGEX" -> assertion.regexAssertion();
            default -> assertion.jsonPathAssertion();
        };
        List<ApiAssertionItemInput> items = group == null ? List.of() : defaultList(group.assertions());
        if (items.isEmpty() && assertion.subject() != null) {
            items = List.of(new ApiAssertionItemInput(null, assertion.subject(), null,
                    legacyCondition(assertion.type(), assertion.operator()), assertion.expectedValue(), true));
        }

        List<ApiAssertionResult> results = new ArrayList<>();
        for (ApiAssertionItemInput item : items) {
            if (item == null || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            String expression = replaceVariables(Optional.ofNullable(item.expression()).orElse(""), variables);
            String condition = normalizeAssertionCondition(item.condition(), legacyCondition(assertion.type(), assertion.operator()));
            String expectedValue = replaceVariables(Optional.ofNullable(item.expectedValue()).orElse(""), variables);
            List<String> values;
            try {
                values = switch (bodyType) {
                    case "X_PATH" -> extractByXPath(response.body(), expression, group == null ? "XML" : group.responseFormat());
                    case "REGEX" -> extractByRegex(response.body(), expression, "EXPRESSION");
                    default -> extractByJsonPath(response.body(), expression);
                };
            } catch (Exception exception) {
                throw new BadRequestException(exception.getMessage());
            }
            AssertionComparison comparison = compareValues(values, condition, expectedValue);
            results.add(new ApiAssertionResult(
                    assertion.id(),
                    "RESPONSE_BODY",
                    firstNonBlank(assertion.name(), "Response Body"),
                    expression,
                    condition,
                    expectedValue,
                    formatActualValues(values),
                    comparison.success(),
                    comparison.message()
            ));
        }
        return results;
    }

    private List<ApiAssertionResult> evaluateVariableAssertions(ApiAssertionInput assertion, Map<String, String> variables) {
        List<ApiAssertionResult> results = new ArrayList<>();
        for (ApiAssertionItemInput item : defaultList(assertion.variableAssertionItems())) {
            if (item == null || Boolean.FALSE.equals(item.enabled())) {
                continue;
            }
            String variableName = replaceVariables(Optional.ofNullable(item.variableName()).orElse(""), variables);
            boolean found = variables.containsKey(variableName);
            String actual = Optional.ofNullable(variables.get(variableName)).orElse("");
            String condition = normalizeAssertionCondition(item.condition(), assertion.condition());
            String expectedValue = replaceVariables(Optional.ofNullable(item.expectedValue()).orElse(""), variables);
            AssertionComparison comparison = compareValue(actual, condition, expectedValue);
            String message = comparison.message();
            if (!found && !comparison.success()) {
                message = "Variable not found: " + variableName + ". " + message;
            }
            results.add(new ApiAssertionResult(
                    assertion.id(),
                    "VARIABLE",
                    firstNonBlank(assertion.name(), "Variable"),
                    variableName,
                    condition,
                    expectedValue,
                    actual,
                    comparison.success(),
                    message
            ));
        }
        return results;
    }

    private ApiAssertionResult evaluateScriptAssertion(
            ApiAssertionInput assertion,
            ApiRequestSnapshot request,
            ApiResponseSnapshot response,
            Map<String, String> variables
    ) {
        String script = Optional.ofNullable(assertion.script()).orElse("");
        String type = "SCRIPT";
        String name = firstNonBlank(assertion.name(), "Script");
        if (script.isBlank()) {
            return new ApiAssertionResult(assertion.id(), type, name, "script", "UNCHECKED", "", "",
                    false, "Script assertion content cannot be blank");
        }
        ApiAutomationScriptRunner.ScriptExecutionResult scriptResult = scriptRunner.execute(
                script,
                new LinkedHashMap<>(variables),
                toRequestContext(request),
                toResponseContext(response)
        );
        variables.clear();
        variables.putAll(scriptResult.variables());
        return new ApiAssertionResult(
                assertion.id(),
                type,
                name,
                "script",
                "UNCHECKED",
                "",
                "",
                scriptResult.success(),
                scriptResult.success() ? "Assertion passed" : scriptResult.message()
        );
    }

    private ApiAssertionResult evaluateSingleAssertion(
            ApiAssertionInput assertion,
            String type,
            String name,
            String subject,
            String condition,
            String actual,
            String rawExpectedValue,
            Map<String, String> variables,
            String fallbackId
    ) {
        String expectedValue = replaceVariables(Optional.ofNullable(rawExpectedValue).orElse(""), variables);
        AssertionComparison comparison = compareValue(Optional.ofNullable(actual).orElse(""), condition, expectedValue);
        return new ApiAssertionResult(
                firstNonBlank(assertion.id(), fallbackId),
                type,
                name,
                subject,
                condition,
                expectedValue,
                Optional.ofNullable(actual).orElse(""),
                comparison.success(),
                comparison.message()
        );
    }

    private AssertionComparison compareValues(List<String> actualValues, String condition, String expectedValue) {
        List<String> values = defaultList(actualValues);
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
            return new AssertionComparison(false, "No value matched expression");
        }
        return values.stream()
                .map(value -> compareValue(value, normalized, expectedValue))
                .filter(AssertionComparison::success)
                .findFirst()
                .orElseGet(() -> comparisonResult(false, formatActualValues(values), expectedValue));
    }

    private AssertionComparison compareValue(String actual, String condition, String expectedValue) {
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

    private AssertionComparison comparisonResult(boolean success, String actual, String expectedValue) {
        return new AssertionComparison(success, success ? "Assertion passed" : "Expected " + expectedValue + " but got " + actual);
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

    private List<String> extractByJsonPath(String source, String expression) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        Object value = JsonPath.read(source, expression == null || expression.isBlank() ? "$" : expression);
        return flattenExtractedValue(value);
    }

    private List<String> extractByXPath(String source, String expression, String responseFormat) throws Exception {
        if (source == null || source.isBlank() || expression == null || expression.isBlank()) {
            return List.of();
        }
        Document document;
        if ("HTML".equalsIgnoreCase(responseFormat)) {
            document = new W3CDom().fromJsoup(Jsoup.parse(source));
        } else {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(source)));
        }
        Object nodeSet = XPathFactory.newInstance().newXPath().evaluate(expression, document, XPathConstants.NODESET);
        if (nodeSet instanceof NodeList nodes && nodes.getLength() > 0) {
            List<String> values = new ArrayList<>();
            for (int index = 0; index < nodes.getLength(); index++) {
                values.add(Optional.ofNullable(nodes.item(index).getTextContent()).orElse(""));
            }
            return values;
        }
        Object result = XPathFactory.newInstance().newXPath().evaluate(expression, document, XPathConstants.STRING);
        return result == null || String.valueOf(result).isBlank() ? List.of() : List.of(String.valueOf(result));
    }

    private List<String> extractByRegex(String source, String expression, String matchingRule) {
        if (source == null || expression == null || expression.isBlank()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(expression, Pattern.DOTALL).matcher(source);
        boolean useGroup = "GROUP".equalsIgnoreCase(matchingRule);
        while (matcher.find()) {
            if (useGroup && matcher.groupCount() > 0) {
                matches.add(matcher.group(1));
            } else {
                matches.add(matcher.group());
            }
        }
        return matches;
    }

    private List<String> flattenExtractedValue(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stringifyExtractedValue).toList();
        }
        return List.of(stringifyExtractedValue(value));
    }

    private String stringifyExtractedValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            return String.valueOf(value);
        }
        return ApiAutomationJsonSupport.toJson(value, "Failed to serialize extracted value");
    }

    private String extractJsonValue(String body, String expression) throws IOException {
        if (body == null || body.isBlank()) {
            return "";
        }
        JsonNode current = OBJECT_MAPPER.readTree(body);
        String normalized = Optional.ofNullable(expression).orElse("").trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return current.isValueNode() ? current.asText() : current.toString();
        }
        for (String segment : normalized.split("\\.")) {
            Matcher matcher = Pattern.compile("([\\w-]+)(\\[(\\d+)])?").matcher(segment);
            if (!matcher.matches()) {
                return "";
            }
            current = current.path(matcher.group(1));
            if (matcher.group(3) != null) {
                current = current.path(Integer.parseInt(matcher.group(3)));
            }
        }
        return current.isMissingNode() || current.isNull() ? "" : (current.isValueNode() ? current.asText() : current.toString());
    }

    private String replaceVariables(String text, Map<String, String> variables) {
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

    private String formatActualValues(List<String> values) {
        return ApiAutomationJsonSupport.toJson(defaultList(values), "Failed to serialize assertion actual values");
    }

    private String findHeaderValue(Map<String, String> headers, String headerName) {
        if (headers == null || headerName == null) {
            return "";
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    private Map<String, Object> toResponseContext(ApiResponseSnapshot response) {
        if (response == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("statusCode", response.statusCode());
        context.put("headers", response.headers() == null ? Map.of() : response.headers());
        context.put("body", response.body());
        context.put("contentType", response.contentType());
        return context;
    }

    private Map<String, Object> toRequestContext(ApiRequestSnapshot request) {
        if (request == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("method", request.method());
        context.put("url", request.url());
        context.put("headers", request.headers() == null ? Map.of() : request.headers());
        context.put("body", request.body());
        return context;
    }

    private String normalizeAssertionType(ApiAssertionInput assertion) {
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

    private String normalizeBodyAssertionType(ApiAssertionInput assertion) {
        String type = Optional.ofNullable(assertion.assertionBodyType()).orElse("").trim().toUpperCase(Locale.ROOT);
        if (!type.isBlank()) {
            return "XPATH".equals(type) ? "X_PATH" : type;
        }
        String legacyType = Optional.ofNullable(assertion.type()).orElse("").trim().toUpperCase(Locale.ROOT);
        if (legacyType.startsWith("BODY_JSONPATH_")) {
            return "JSON_PATH";
        }
        return "JSON_PATH";
    }

    private String legacyCondition(String type, String operator) {
        String normalizedType = Optional.ofNullable(type).orElse("").trim().toUpperCase(Locale.ROOT);
        if ("HEADER_CONTAINS".equals(normalizedType) || "BODY_JSONPATH_CONTAINS".equals(normalizedType)) {
            return "CONTAINS";
        }
        if ("RESPONSE_TIME_LE".equals(normalizedType)) {
            return "LT_OR_EQUALS";
        }
        String normalizedOperator = Optional.ofNullable(operator).orElse("").trim().toUpperCase(Locale.ROOT);
        if (!normalizedOperator.isBlank()) {
            return normalizedOperator;
        }
        return "EQUALS";
    }

    private String normalizeAssertionCondition(String condition, String fallback) {
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

    private String defaultAssertionName(String type) {
        return switch (type) {
            case "RESPONSE_CODE" -> "Status Code";
            case "RESPONSE_HEADER" -> "Response Header";
            case "RESPONSE_BODY" -> "Response Body";
            case "RESPONSE_TIME" -> "Response Time";
            case "VARIABLE" -> "Variable";
            case "SCRIPT" -> "Script";
            default -> "Assertion";
        };
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
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

    private record AssertionComparison(boolean success, String message) {
    }
}
