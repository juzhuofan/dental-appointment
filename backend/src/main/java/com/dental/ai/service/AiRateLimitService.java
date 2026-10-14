package com.dental.ai.service;

import com.dental.ai.config.AiProperties;
import com.dental.common.BusinessException;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Redis 3.2 兼容的原子计数，接口按账号限制付费模型调用次数。 */
@Service
public class AiRateLimitService {

    private static final DefaultRedisScript<Long> COUNTER = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], 60); end; return n",
            Long.class);
    private final StringRedisTemplate redisTemplate;
    private final AiProperties properties;

    public AiRateLimitService(StringRedisTemplate redisTemplate, AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void check(long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        Long count;
        try {
            count = redisTemplate.execute(COUNTER, List.of("dental:ai:rate:" + userId));
        } catch (RuntimeException exception) {
            throw new BusinessException("AI_UNAVAILABLE", "智能助手暂时不可用，请手动选择医生",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (count == null) {
            throw new BusinessException("AI_UNAVAILABLE", "智能助手暂时不可用，请手动选择医生",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (count > properties.getRequestsPerMinute()) {
            throw new BusinessException("AI_RATE_LIMITED", "请求较频繁，请一分钟后再试",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
