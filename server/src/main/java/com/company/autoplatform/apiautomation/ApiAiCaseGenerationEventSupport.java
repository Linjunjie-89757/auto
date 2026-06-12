package com.company.autoplatform.apiautomation;

import com.company.autoplatform.apiautomation.ApiAiCaseGenerationService.ApiAiCaseGenerationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

@Component
class ApiAiCaseGenerationEventSupport {

    private final ObjectMapper objectMapper;

    ApiAiCaseGenerationEventSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void writeEvent(Writer writer, ApiAiCaseGenerationEvent event) throws IOException {
        writer.write("event: ");
        writer.write(event.event());
        writer.write("\n");
        writer.write("data: ");
        writer.write(objectMapper.writeValueAsString(event));
        writer.write("\n\n");
        writer.flush();
    }

    void writeUnchecked(Writer writer, ApiAiCaseGenerationEvent event) {
        try {
            writeEvent(writer, event);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    int indexOfLineBreak(StringBuilder builder) {
        for (int index = 0; index < builder.length(); index++) {
            char value = builder.charAt(index);
            if (value == '\n' || value == '\r') {
                return index;
            }
        }
        return -1;
    }
}
