package com.company.autoplatform.common;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, long pageNo, long pageSize, long totalPages) {

    public PageResponse(List<T> items, long total) {
        this(items, total, 1, total, total <= 0 ? 0 : 1);
    }

    public static <T> PageResponse<T> of(List<T> items, long total, long pageNo, long pageSize) {
        long safePageSize = pageSize <= 0 ? Math.max(total, 1) : pageSize;
        long totalPages = total <= 0 ? 0 : (long) Math.ceil((double) total / safePageSize);
        return new PageResponse<>(items, total, pageNo, safePageSize, totalPages);
    }
}
