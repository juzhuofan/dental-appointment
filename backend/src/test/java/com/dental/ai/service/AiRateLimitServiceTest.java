package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dental.ai.config.AiProperties;
import com.dental.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class AiRateLimitServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AiProperties properties = new AiProperties();
    private final AiRateLimitService service = new AiRateLimitService(redis, properties);

    @Test
    void rejectsOverQuotaWithoutExposingRedisFailure() {
        properties.setEnabled(true);
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(11L);
        assertEquals("AI_RATE_LIMITED", assertThrows(BusinessException.class, () -> service.check(1)).getCode());
        when(redis.execute(any(RedisScript.class), anyList())).thenThrow(new RuntimeException("redis-password"));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.check(1));
        assertEquals("AI_UNAVAILABLE", failure.getCode());
        assertEquals("智能助手暂时不可用，请手动选择医生", failure.getMessage());
    }

    @Test
    void disabledAiDoesNotUseRedis() {
        service.check(1);
        verifyNoInteractions(redis);
    }
}
