package com.company.autoplatform.ai;

import java.util.List;

record AiModelFetchResult(
        List<AiProviderModelItem> models,
        String message
) {
}
