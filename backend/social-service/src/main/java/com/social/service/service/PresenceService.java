package com.social.service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 在线状态（Redis）。STOMP 连接建立时置在线、断开时清除（见 social-websocket PresenceListener）。
 * key 带 TTL，避免异常断开导致僵尸在线。
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String KEY_PREFIX = "presence:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    public boolean online(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
    }

    public void markOnline(Long userId) {
        redis.opsForValue().set(KEY_PREFIX + userId, "1", TTL);
    }

    public void markOffline(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
