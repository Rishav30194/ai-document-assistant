package com.rishav.aidocumentassistant.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishav.aidocumentassistant.model.ConversationTurn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final String KEY_PREFIX = "chat:session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chat.session.ttl-hours:24}")
    private int ttlHours;

    public List<ConversationTurn> getHistory(String sessionId) {
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize history for session {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    public void appendTurn(String sessionId, ConversationTurn turn) {
        List<ConversationTurn> history = getHistory(sessionId);
        history.add(turn);
        saveHistory(sessionId, history);
    }

    public void deleteSession(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private void saveHistory(String sessionId, List<ConversationTurn> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key(sessionId), json, ttlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to save history for session {}", sessionId, e);
        }
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
