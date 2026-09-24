package com.company.projectmanagement.identity.security;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按“来源地址 + 用户名”限制连续登录失败。
 *
 * <p>当前部署是单实例，因此使用有界的进程内计数器；切换多实例时需改用共享存储。
 */
@Component
public class LoginAttemptLimiter {

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long windowMillis;
    private final long blockMillis;
    private final int maxEntries;
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter(
            @Value("${app.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${app.login-rate-limit.window-seconds:900}") long windowSeconds,
            @Value("${app.login-rate-limit.block-seconds:900}") long blockSeconds,
            @Value("${app.login-rate-limit.max-entries:10000}") int maxEntries) {
        this(maxFailures, windowSeconds, blockSeconds, maxEntries, Clock.systemUTC());
    }

    LoginAttemptLimiter(
            int maxFailures,
            long windowSeconds,
            long blockSeconds,
            int maxEntries,
            Clock clock) {
        this.maxFailures = Math.max(1, maxFailures);
        this.windowMillis = Math.max(1, windowSeconds) * 1_000;
        this.blockMillis = Math.max(1, blockSeconds) * 1_000;
        this.maxEntries = Math.max(100, maxEntries);
        this.clock = clock;
    }

    /** 在密码校验前拒绝尚未解除的限制。 */
    public void checkAllowed(String sourceAddress, String username) {
        String key = key(sourceAddress, username);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }
        long now = clock.millis();
        if (state.blockedUntilMillis() > now) {
            throw new LoginRateLimitException(secondsUntil(state.blockedUntilMillis(), now));
        }
        if (now - state.windowStartedMillis() >= windowMillis) {
            attempts.remove(key, state);
        }
    }

    /** 记录失败；达到阈值的当次请求立即返回 429。 */
    public void recordFailure(String sourceAddress, String username) {
        long now = clock.millis();
        ensureCapacity(now);
        AttemptState state = attempts.compute(key(sourceAddress, username), (ignored, current) -> {
            if (current == null || now - current.windowStartedMillis() >= windowMillis) {
                return new AttemptState(1, now, 0);
            }
            int failures = current.failures() + 1;
            long blockedUntil = failures >= maxFailures ? now + blockMillis : 0;
            return new AttemptState(failures, current.windowStartedMillis(), blockedUntil);
        });
        if (state.blockedUntilMillis() > now) {
            throw new LoginRateLimitException(secondsUntil(state.blockedUntilMillis(), now));
        }
    }

    /** 成功登录后清除对应失败记录。 */
    public void recordSuccess(String sourceAddress, String username) {
        attempts.remove(key(sourceAddress, username));
    }

    private void ensureCapacity(long now) {
        if (attempts.size() < maxEntries) {
            return;
        }
        attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        if (attempts.size() >= maxEntries) {
            // 容量耗尽时失败关闭，防止攻击者用随机用户名耗尽内存。
            throw new LoginRateLimitException(Math.max(1, blockMillis / 1_000));
        }
    }

    private boolean isExpired(AttemptState state, long now) {
        return state.blockedUntilMillis() <= now
                && now - state.windowStartedMillis() >= windowMillis;
    }

    private String key(String sourceAddress, String username) {
        String source = sourceAddress == null || sourceAddress.isBlank()
                ? "unknown"
                : sourceAddress.trim();
        String normalizedUsername = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);
        return source + '\n' + normalizedUsername;
    }

    private long secondsUntil(long futureMillis, long nowMillis) {
        return Math.max(1, (futureMillis - nowMillis + 999) / 1_000);
    }

    private record AttemptState(int failures, long windowStartedMillis, long blockedUntilMillis) {}
}
