package com.company.autoplatform.user;

import java.util.List;

public record BatchCreateUserResponse(
        Integer total,
        Integer successCount,
        Integer failureCount,
        List<BatchCreateUserItem> results
) {
}
