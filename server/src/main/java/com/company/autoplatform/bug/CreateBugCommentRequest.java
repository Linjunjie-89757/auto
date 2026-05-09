package com.company.autoplatform.bug;

import jakarta.validation.constraints.NotBlank;

public record CreateBugCommentRequest(
        String workspaceCode,
        @NotBlank(message = "评论内容不能为空") String content
) {
}
