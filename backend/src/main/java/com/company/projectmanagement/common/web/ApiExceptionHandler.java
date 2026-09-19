package com.company.projectmanagement.common.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("INVALID_CREDENTIALS", "用户名或密码错误"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationFailure() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "请求参数校验失败"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableRequest() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "请求内容格式不正确"));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleRequestParameterValidationFailure() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", "请求参数校验失败"));
    }
}
