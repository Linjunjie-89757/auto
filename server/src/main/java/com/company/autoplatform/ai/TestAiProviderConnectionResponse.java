package com.company.autoplatform.ai;

import java.time.LocalDateTime;

public record TestAiProviderConnectionResponse(
        boolean success,
        Long connectionId,
        String connectionName,
        String protocolType,
        String message,
        LocalDateTime verifiedAt
) {
}
