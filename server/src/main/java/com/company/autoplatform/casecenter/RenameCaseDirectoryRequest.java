package com.company.autoplatform.casecenter;

import jakarta.validation.constraints.NotBlank;

public record RenameCaseDirectoryRequest(
        @NotBlank(message = "目录名称不能为空") String name
) {
}
