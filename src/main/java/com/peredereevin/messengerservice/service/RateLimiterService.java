package com.peredereevin.messengerservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final long minIntervalMillis;

    public RateLimiterService(StringRedisTemplate redisTemplate,
                              @Value("${rate-limit.send-interval-ms:5000}") long minIntervalMillis) {
        this.redisTemplate = redisTemplate;
        this.minIntervalMillis = minIntervalMillis;
    }

    /**
     * Проверяет, можно ли отправить сообщение.
     * @param key уникальный ключ (например, userId + platform)
     * @return true, если лимит не превышен
     */
    public boolean allow(String key) {
        String redisKey = "rate_limit:" + key;
        try {
            // Пытаемся установить ключ только если он отсутствует, с TTL = minIntervalMillis
            Boolean absent = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", Duration.ofMillis(minIntervalMillis));
            if (Boolean.TRUE.equals(absent)) {
                return true;   // ключа не было, можно отправлять
            }
            log.warn("Rate limit exceeded for key: {}", key);
            return false;
        } catch (Exception e) {
            log.error("Redis error in rate limiter, allowing request as fallback", e);
            return true;   // при сбое Redis разрешаем отправку (или можно false для строгости)
        }
    }
}