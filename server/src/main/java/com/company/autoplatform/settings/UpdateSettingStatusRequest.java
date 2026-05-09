package com.company.autoplatform.settings;

import jakarta.validation.constraints.NotNull;

public record UpdateSettingStatusRequest(
        @NotNull(message = "状态不能为空") Integer status
) {
}
