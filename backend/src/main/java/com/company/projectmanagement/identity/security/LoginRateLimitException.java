package com.company.projectmanagement.identity.security;

/** 登录失败达到阈值时携带建议重试时间。 */
public class LoginRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitException(long retryAfterSeconds) {
        super("登录尝试过于频繁，请稍后再试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
