package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

final class ApiAutomationJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private ApiAutomationJsonSupport() {
    }

    static String toJson(Object value, String message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException(message);
        }
    }

    static <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    static <T> List<T> readList(String json, TypeReference<List<T>> type, List<T> fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }
}
