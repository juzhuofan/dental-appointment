package com.dental.ai.service;

import com.dental.common.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 会话级短期锁，模型调用期间不持有数据库事务或行锁。 */
@Service
public class AiConversationLockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiConversationLockService.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0", Long.class);
    private final StringRedisTemplate redis;

    public AiConversationLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public <T> T withLock(long conversationId, Supplier<T> action) {
        String key = "dental:ai:conversation:lock:" + conversationId;
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(90));
        } catch (RuntimeException exception) {
            throw new BusinessException("AI_UNAVAILABLE", "聊天服务暂时不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("AI_CONVERSATION_BUSY", "这个会话正在回复，请稍后重试", HttpStatus.CONFLICT);
        }
        try {
            return action.get();
        } finally {
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (RuntimeException exception) {
                // 已提交的回复不能因释放锁失败而误报失败；锁会自动过期。
                LOGGER.warn("AI conversation lock release failed: type={}", exception.getClass().getSimpleName());
            }
        }
    }
}
