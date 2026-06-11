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
    private final ApiAssertionSupport assertionSupport;

    public ApiAssertionEvaluator(ApiAutomationScriptRunner scriptRunner, ApiAssertionSupport assertionSupport) {
        this.scriptRunner = scriptRunner;
        this.assertionSupport = assertionSupport;
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
            String type = assertionSupport.normalizeAssertionType(assertion);
            try {
                switch (type) {
                    case "RESPONSE_CODE" -> results.add(evaluateSingleAssertion(
                            assertion,
                            type,
                            firstNonBlank(assertion.name(), "Status Code"),
                            "statusCode",
                            assertionSupport.normalizeAssertionCondition(assertion.condition(), legacyCondition(assertion.type(), assertion.operator())),
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
                            assertionSupport.normalizeAssertionCondition(assertion.condition(), "LT_OR_EQUALS"),
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
                        assertionSupport.normalizeAssertionCondition(assertion.condition(), legacyCondition(assertion.type(), assertion.operator())),
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
                    assertionSupport.normalizeAssertionCondition(item.condition(), legacyCondition(assertion.type(), assertion.operator())),
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
            String condition = assertionSupport.normalizeAssertionCondition(item.condition(), legacyCondition(assertion.type(), assertion.operator()));
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
            ApiAssertionSupport.ApiAssertionComparison comparison = assertionSupport.compareValues(values, condition, expectedValue);
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
            String condition = assertionSupport.normalizeAssertionCondition(item.condition(), assertion.condition());
            String expectedValue = replaceVariables(Optional.ofNullable(item.expectedValue()).orElse(""), variables);
            ApiAssertionSupport.ApiAssertionComparison comparison = assertionSupport.compareValue(actual, condition, expectedValue);
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
        ApiAssertionSupport.ApiAssertionComparison comparison = assertionSupport.compareValue(Optional.ofNullable(actual).orElse(""), condition, expectedValue);
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
}
