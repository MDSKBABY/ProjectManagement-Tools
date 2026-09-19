package com.company.projectmanagement.common.web;

public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message));
    }

    public record ApiError(String code, String message) {
    }
}
