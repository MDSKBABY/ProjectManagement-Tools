package com.company.projectmanagement.common.web;

import java.util.List;

public record PageResponse<T>(List<T> data, Pagination pagination) {

    public static <T> PageResponse<T> of(
            List<T> data, int page, int pageSize, long totalItems) {
        long totalPages = totalItems == 0 ? 0 : (totalItems + pageSize - 1) / pageSize;
        return new PageResponse<>(data, new Pagination(page, pageSize, totalItems, totalPages));
    }

    public record Pagination(int page, int pageSize, long totalItems, long totalPages) {
    }
}
