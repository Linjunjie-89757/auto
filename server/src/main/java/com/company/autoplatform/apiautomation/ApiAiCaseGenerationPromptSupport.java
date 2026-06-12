package com.company.autoplatform.apiautomation;

import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiCaseGenerationRequest;
import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiCaseGenerationSlot;
import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiGeneratedCaseOutline;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class ApiAiCaseGenerationPromptSupport {

    private final ObjectMapper objectMapper;

    ApiAiCaseGenerationPromptSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String buildPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            int index,
            int total
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        payload.put("target", Map.of(
                "index", index,
                "total", total,
                "group", slot.group(),
                "groupKey", slot.groupKey(),
                "type", slot.type(),
                "typeKey", slot.typeKey(),
                "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
        ));
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\u548c\u76ee\u6807\u7c7b\u578b\uff0c\u53ea\u751f\u6210 1 \u6761\u9ad8\u8d28\u91cf\u63a5\u53e3\u7528\u4f8b\u3002
                \u53ea\u8fd4\u56de\u4e25\u683c JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\u3001\u89e3\u91ca\u6587\u5b57\u6216\u4ee3\u7801\u5757\u3002
                \u8fd4\u56de\u7ed3\u6784\u5fc5\u987b\u5339\u914d\u4e0b\u9762\u793a\u4f8b\uff1a
                {
                  "name": "\u7528\u4f8b\u7c7b\u578b \u2013 \u7528\u4f8b\u573a\u666f \u2013 \u671f\u671b\u7ed3\u679c",
                  "description": "\u7528\u4f8b\u8bf4\u660e",
                  "tags": ["\u6807\u7b7e"],
                  "expected": "\u9884\u671f\u7ed3\u679c",
                  "requestConfig": {
                    "method": "GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE",
                    "path": "\u63a5\u53e3\u8def\u5f84",
                    "timeoutMs": 10000,
                    "queryParams": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "headers": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "cookies": [],
                    "body": {"type":"NONE|FORM_DATA|X_WWW_FORM_URLENCODED|RAW_JSON|RAW_XML|RAW_TEXT|BINARY","rawText":"","formItems":[],"contentType":"","fileName":"","binaryBase64":""},
                    "authConfig": {"authType":"NONE|BASIC|DIGEST","basicAuth":{"userName":"","password":""},"digestAuth":{"userName":"","password":""}}
                  },
                  "assertions": [{"type":"STATUS_CODE","subject":"STATUS_CODE","operator":"LT","expectedValue":"400","enabled":true}],
                  "preProcessors": [],
                  "postProcessors": []
                }
                \u89c4\u5219\uff1a
                1. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                2. \u53c2\u6570\u5b57\u6bb5\u5c3d\u91cf\u8865\u5168 required\u3001paramType\u3001minLength\u3001maxLength\u3001description\u3001enabled\u3002
                3. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                4. assertions \u8981\u80fd\u9a8c\u8bc1 expected\uff0c\u6b63\u5411\u7528\u4f8b\u901a\u5e38\u65ad\u8a00 2xx/3xx\uff0c\u53cd\u5411\u6216\u5b89\u5168\u7528\u4f8b\u901a\u5e38\u65ad\u8a00\u5408\u7406\u9519\u8bef\u7801\u6216\u9519\u8bef\u4fe1\u606f\u3002
                5. name \u4e0d\u8981\u5305\u542b\u5206\u7ec4\u524d\u7f00\uff0c\u4f8b\u5982\u4e0d\u8981\u5199\u3010\u6b63\u5411\u3011\u3002\u63a8\u8350\u683c\u5f0f\uff1a\u7c7b\u578b \u2013 \u573a\u666f\u63cf\u8ff0 \u2013 \u671f\u671b\u8fd4\u56de\u7ed3\u679c\u3002
                6. expected \u7528\u4e00\u53e5\u8bdd\u63cf\u8ff0\u65ad\u8a00\u610f\u56fe\uff0c\u4e0d\u8981\u4e3a\u7a7a\u3002
                7. \u6240\u6709\u8f93\u51fa\u5fc5\u987b\u662f\u5408\u6cd5 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    String buildPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            ApiAiGeneratedCaseOutline outline,
            int index,
            int total
    ) {
        Map<String, Object> payload = buildSingleCasePayload(workspace, request, slot, index, total);
        payload.put("outline", outline);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\u548c\u5df2\u786e\u5b9a\u7684\u7528\u4f8b\u5927\u7eb2\uff0c\u53ea\u751f\u6210 1 \u6761\u5b8c\u6574\u63a5\u53e3\u7528\u4f8b\u8be6\u60c5\u3002
                \u53ea\u8fd4\u56de\u4e25\u683c JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\u3001\u89e3\u91ca\u6587\u5b57\u6216\u4ee3\u7801\u5757\u3002
                \u8fd4\u56de\u7ed3\u6784\u5fc5\u987b\u5339\u914d\u4e0b\u9762\u793a\u4f8b\uff1a
                {
                  "name": "\u5927\u7eb2\u4e2d\u7684 name",
                  "description": "\u7528\u4f8b\u8bf4\u660e",
                  "tags": ["\u6807\u7b7e"],
                  "expected": "\u9884\u671f\u7ed3\u679c",
                  "requestConfig": {
                    "method": "GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE",
                    "path": "\u63a5\u53e3\u8def\u5f84",
                    "timeoutMs": 10000,
                    "queryParams": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "headers": [{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],
                    "cookies": [],
                    "body": {"type":"NONE|FORM_DATA|X_WWW_FORM_URLENCODED|RAW_JSON|RAW_XML|RAW_TEXT|BINARY","rawText":"","formItems":[],"contentType":"","fileName":"","binaryBase64":""},
                    "authConfig": {"authType":"NONE|BASIC|DIGEST","basicAuth":{"userName":"","password":""},"digestAuth":{"userName":"","password":""}}
                  },
                  "assertions": [{"type":"STATUS_CODE","subject":"STATUS_CODE","operator":"LT","expectedValue":"400","enabled":true}],
                  "preProcessors": [],
                  "postProcessors": []
                }
                \u89c4\u5219\uff1a
                1. name\u3001description\u3001tags\u3001expected \u8981\u4e0e outline \u4fdd\u6301\u4e00\u81f4\uff0c\u4e0d\u8981\u66f4\u6362\u6210\u5176\u4ed6\u573a\u666f\u3002
                2. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                3. requestConfig \u8981\u4f53\u73b0 outline.type \u5bf9\u5e94\u7684\u7528\u4f8b\u5dee\u5f02\uff0c\u4f8b\u5982\u5fc5\u586b\u7f3a\u5931\u3001\u8fb9\u754c\u503c\u3001\u5f02\u5e38\u503c\u6216\u5b89\u5168\u8f93\u5165\u3002
                4. assertions \u8981\u80fd\u9a8c\u8bc1 expected\uff0c\u6b63\u5411\u7528\u4f8b\u901a\u5e38\u65ad\u8a00 2xx/3xx\uff0c\u53cd\u5411\u6216\u5b89\u5168\u7528\u4f8b\u901a\u5e38\u65ad\u8a00\u5408\u7406\u9519\u8bef\u7801\u6216\u9519\u8bef\u4fe1\u606f\u3002
                5. \u6240\u6709\u8f93\u51fa\u5fc5\u987b\u662f\u5408\u6cd5 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    String buildOutlinePrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        List<Map<String, Object>> targets = buildTargets(request, slots);
        payload.put("targets", targets);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u8bbe\u8ba1\u52a9\u624b\u3002\u8bf7\u5148\u4e3a targets \u4e2d\u7684\u6bcf\u4e2a\u76ee\u6807\u8bbe\u8ba1 1 \u6761\u7528\u4f8b\u5927\u7eb2\uff0c\u4e0d\u8981\u751f\u6210\u5b8c\u6574\u8bf7\u6c42\u914d\u7f6e\u3002
                \u5fc5\u987b\u4f7f\u7528 NDJSON \u8f93\u51fa\uff1a\u4e00\u884c\u4e00\u6761\u5b8c\u6574 JSON\uff0c\u6bcf\u884c\u5bf9\u5e94\u4e00\u4e2a target\u3002\u4e0d\u8981\u8f93\u51fa Markdown\u3001\u4ee3\u7801\u5757\u3001\u89e3\u91ca\u6587\u5b57\u6216 JSON \u6570\u7ec4\u3002
                \u6bcf\u4e00\u884c\u5fc5\u987b\u662f\u5408\u6cd5 JSON\uff0c\u7ed3\u6784\u5982\u4e0b\uff1a
                {"id":"target id","outline":{"name":"\u7c7b\u578b \u2013 \u540d\u79f0 \u2013 \u671f\u671b","description":"\u8bf4\u660e","group":"\u6b63\u5411","groupKey":"positive","type":"\u4ec5\u4f20\u5fc5\u8981\u5b57\u6bb5","typeKey":"required-only","expected":"\u671f\u671b\u8fd4\u56de200\u6210\u529f","tags":["\u4ec5\u4f20\u5fc5\u8981\u5b57\u6bb5"]}}
                \u89c4\u5219\uff1a
                1. \u6bcf\u4e2a target \u5fc5\u987b\u8f93\u51fa\u4e00\u884c\uff0c\u8f93\u51fa\u987a\u5e8f\u5c3d\u91cf\u548c targets \u4e00\u81f4\u3002
                2. \u6bcf\u884c id \u5fc5\u987b\u7b49\u4e8e\u5bf9\u5e94 target.id\u3002
                3. outline.name \u5fc5\u987b\u4f7f\u7528\u201c\u7c7b\u578b \u2013 \u540d\u79f0 \u2013 \u671f\u671b\u201d\u683c\u5f0f\uff0c\u7c7b\u578b\u4f7f\u7528 target.type\u3002
                4. outline.group/groupKey/type/typeKey \u5fc5\u987b\u4e0e\u5bf9\u5e94 target \u4fdd\u6301\u4e00\u81f4\u3002
                5. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                6. \u5927\u7eb2\u53ea\u63cf\u8ff0\u8981\u751f\u6210\u7684\u7528\u4f8b\u610f\u56fe\uff0c\u4e0d\u8981\u8f93\u51fa requestConfig\u3001assertions\u3001preProcessors\u3001postProcessors\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    String buildBatchPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = buildBatchPayload(workspace, request, slots);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\uff0c\u4e3a targets \u4e2d\u7684\u6bcf\u4e2a\u76ee\u6807\u5404\u751f\u6210 1 \u6761\u63a5\u53e3\u7528\u4f8b\u3002
                \u53ea\u8fd4\u56de\u4e25\u683c JSON\uff0c\u4e0d\u8981\u8fd4\u56de Markdown\u3001\u89e3\u91ca\u6587\u5b57\u6216\u4ee3\u7801\u5757\u3002
                \u8fd4\u56de\u683c\u5f0f\uff1a{"cases":[case, case]}
                \u6bcf\u4e2a case \u5b57\u6bb5\u8981\u5305\u542b name\u3001description\u3001tags\u3001expected\u3001requestConfig\u3001assertions\u3001preProcessors\u3001postProcessors\u3002
                \u89c4\u5219\uff1a
                1. cases \u6570\u91cf\u5fc5\u987b\u7b49\u4e8e targets \u6570\u91cf\uff0c\u5e76\u4e14\u987a\u5e8f\u5fc5\u987b\u548c targets \u4e00\u81f4\u3002
                2. \u6bcf\u6761 case \u7684\u573a\u666f\u5fc5\u987b\u8d34\u5408\u5bf9\u5e94 target.type\u3002
                3. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                4. \u53c2\u6570\u5b57\u6bb5\u5c3d\u91cf\u8865\u5168 required\u3001paramType\u3001minLength\u3001maxLength\u3001description\u3001enabled\u3002
                5. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                6. name \u4e0d\u8981\u5305\u542b\u5206\u7ec4\u524d\u7f00\uff0c\u63a8\u8350\u683c\u5f0f\uff1a\u7c7b\u578b \u2013 \u573a\u666f\u63cf\u8ff0 \u2013 \u671f\u671b\u8fd4\u56de\u7ed3\u679c\u3002
                7. expected \u7528\u4e00\u53e5\u8bdd\u63cf\u8ff0\u65ad\u8a00\u610f\u56fe\uff0c\u4e0d\u8981\u4e3a\u7a7a\u3002
                8. \u6240\u6709\u8f93\u51fa\u5fc5\u987b\u662f\u5408\u6cd5 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    String buildStreamingBatchPrompt(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = buildBatchPayload(workspace, request, slots);
        return """
                \u4f60\u662f\u63a5\u53e3\u81ea\u52a8\u5316\u6d4b\u8bd5\u7528\u4f8b\u751f\u6210\u52a9\u624b\u3002\u8bf7\u57fa\u4e8e\u8f93\u5165\u7684\u63a5\u53e3\u5b9a\u4e49\uff0c\u4e3a targets \u4e2d\u7684\u6bcf\u4e2a\u76ee\u6807\u5404\u751f\u6210 1 \u6761\u63a5\u53e3\u7528\u4f8b\u3002
                \u5fc5\u987b\u4f7f\u7528 NDJSON \u8f93\u51fa\uff1a\u4e00\u884c\u4e00\u6761\u5b8c\u6574 JSON\uff0c\u6bcf\u884c\u5bf9\u5e94\u4e00\u4e2a target\u3002\u4e0d\u8981\u8f93\u51fa Markdown\u3001\u4ee3\u7801\u5757\u3001\u89e3\u91ca\u6587\u5b57\u6216 JSON \u6570\u7ec4\u3002
                \u6bcf\u4e00\u884c\u5fc5\u987b\u662f\u5408\u6cd5 JSON\uff0c\u7ed3\u6784\u5982\u4e0b\uff1a
                {"id":"target id","case":{"name":"\u7528\u4f8b\u7c7b\u578b \u2013 \u7528\u4f8b\u573a\u666f \u2013 \u671f\u671b\u7ed3\u679c","description":"\u7528\u4f8b\u8bf4\u660e","tags":["\u6807\u7b7e"],"expected":"\u9884\u671f\u7ed3\u679c","requestConfig":{"method":"GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/TRACE","path":"\u63a5\u53e3\u8def\u5f84","timeoutMs":10000,"queryParams":[{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],"headers":[{"key":"","value":"","description":"","enabled":true,"paramType":"STRING","required":false,"encode":true,"minLength":null,"maxLength":null}],"cookies":[],"body":{"type":"NONE|FORM_DATA|X_WWW_FORM_URLENCODED|RAW_JSON|RAW_XML|RAW_TEXT|BINARY","rawText":"","formItems":[],"contentType":"","fileName":"","binaryBase64":""},"authConfig":{"authType":"NONE|BASIC|DIGEST","basicAuth":{"userName":"","password":""},"digestAuth":{"userName":"","password":""}}},"assertions":[{"type":"STATUS_CODE","subject":"STATUS_CODE","operator":"LT","expectedValue":"400","enabled":true}],"preProcessors":[],"postProcessors":[]}}
                \u89c4\u5219\uff1a
                1. \u6bcf\u4e2a target \u5fc5\u987b\u8f93\u51fa\u4e00\u884c\uff0c\u8f93\u51fa\u987a\u5e8f\u5c3d\u91cf\u548c targets \u4e00\u81f4\u3002
                2. \u6bcf\u884c\u7684 id \u5fc5\u987b\u7b49\u4e8e\u5bf9\u5e94 target.id\u3002
                3. \u6bcf\u6761 case \u7684\u573a\u666f\u5fc5\u987b\u8d34\u5408\u5bf9\u5e94 target.type\u3002
                4. \u4fdd\u7559\u539f\u63a5\u53e3 method/path\uff0c\u9664\u975e\u76ee\u6807\u7528\u4f8b\u660e\u786e\u9700\u8981\u4fee\u6539\u53c2\u6570\u3001\u8bf7\u6c42\u5934\u3001\u8bf7\u6c42\u4f53\u6216\u8ba4\u8bc1\u4fe1\u606f\u3002
                5. \u53c2\u6570\u5b57\u6bb5\u5c3d\u91cf\u8865\u5168 required\u3001paramType\u3001minLength\u3001maxLength\u3001description\u3001enabled\u3002
                6. \u5982\u679c noDuplicate \u4e3a true\uff0c\u8bf7\u907f\u5f00 existingCases \u4e2d\u5df2\u6709\u573a\u666f\u3002
                7. name \u4e0d\u8981\u5305\u542b\u5206\u7ec4\u524d\u7f00\uff0c\u4f8b\u5982\u4e0d\u8981\u5199\u3010\u6b63\u5411\u3011\u3002\u63a8\u8350\u683c\u5f0f\uff1a\u7c7b\u578b \u2013 \u573a\u666f\u63cf\u8ff0 \u2013 \u671f\u671b\u8fd4\u56de\u7ed3\u679c\u3002
                8. expected \u7528\u4e00\u53e5\u8bdd\u63cf\u8ff0\u65ad\u8a00\u610f\u56fe\uff0c\u4e0d\u8981\u4e3a\u7a7a\uff0cassertions \u8981\u80fd\u9a8c\u8bc1 expected\u3002
                9. \u8f93\u51fa\u8fc7\u7a0b\u4e2d\u4e0d\u8981\u8fd4\u56de {"cases":[...]}\uff0c\u53ea\u8fd4\u56de\u4e00\u884c\u4e00\u6761 JSON\u3002
                \u8f93\u5165\u6570\u636e\uff1a
                %s
                """.formatted(toJson(payload));
    }

    private Map<String, Object> buildSingleCasePayload(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            ApiAiCaseGenerationSlot slot,
            int index,
            int total
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        payload.put("target", Map.of(
                "index", index,
                "total", total,
                "group", slot.group(),
                "groupKey", slot.groupKey(),
                "type", slot.type(),
                "typeKey", slot.typeKey(),
                "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
        ));
        return payload;
    }

    private Map<String, Object> buildBatchPayload(
            WorkspaceEntity workspace,
            ApiAiCaseGenerationRequest request,
            List<ApiAiCaseGenerationSlot> slots
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceName", workspace.getWorkspaceName());
        payload.put("definition", Map.of(
                "id", request.definitionId(),
                "name", firstNonBlank(request.definitionName(), request.name(), "\u672a\u547d\u540d\u63a5\u53e3"),
                "method", firstNonBlank(request.method(), request.requestConfig() == null ? null : request.requestConfig().method(), "GET"),
                "path", firstNonBlank(request.path(), request.requestConfig() == null ? null : request.requestConfig().path(), "/"),
                "description", Optional.ofNullable(request.description()).orElse("")
        ));
        payload.put("sourceRequestConfig", request.requestConfig());
        payload.put("sourceAssertions", defaultList(request.assertions(), List.of()));
        payload.put("sourcePreProcessors", defaultList(request.preProcessors(), List.of()));
        payload.put("sourcePostProcessors", defaultList(request.postProcessors(), List.of()));
        payload.put("existingCases", defaultList(request.existingCases(), List.of()));
        payload.put("targets", buildTargets(request, slots));
        return payload;
    }

    private List<Map<String, Object>> buildTargets(ApiAiCaseGenerationRequest request, List<ApiAiCaseGenerationSlot> slots) {
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            ApiAiCaseGenerationSlot slot = slots.get(index);
            targets.add(Map.of(
                    "index", index + 1,
                    "id", slot.id(),
                    "group", slot.group(),
                    "groupKey", slot.groupKey(),
                    "type", slot.type(),
                    "typeKey", slot.typeKey(),
                    "noDuplicate", Boolean.TRUE.equals(request.noDuplicate()),
                    "extraRequirement", Optional.ofNullable(request.prompt()).orElse("")
            ));
        }
        return targets;
    }

    String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private <T> List<T> defaultList(List<T> primary, List<T> fallback) {
        return primary == null ? (fallback == null ? List.of() : fallback) : primary;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }
}
