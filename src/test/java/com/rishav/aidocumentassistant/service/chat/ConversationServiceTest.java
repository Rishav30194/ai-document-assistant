package com.rishav.aidocumentassistant.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishav.aidocumentassistant.model.ConversationTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ConversationService conversationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(conversationService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(conversationService, "ttlHours", 24);
    }

    @Test
    void getHistory_returnsEmptyList_whenSessionDoesNotExist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        List<ConversationTurn> history = conversationService.getHistory("session-1");

        assertThat(history).isEmpty();
    }

    @Test
    void getHistory_returnsPersistedTurns() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        List<ConversationTurn> turns = List.of(
                new ConversationTurn("user", "Hello"),
                new ConversationTurn("assistant", "Hi there")
        );
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(turns));

        List<ConversationTurn> result = conversationService.getHistory("session-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("user");
        assertThat(result.get(0).content()).isEqualTo("Hello");
        assertThat(result.get(1).role()).isEqualTo("assistant");
    }

    @Test
    void appendTurn_savesNewTurnWithTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        conversationService.appendTurn("session-1", new ConversationTurn("user", "Question?"));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("chat:session:session-1"), jsonCaptor.capture(), eq(24L), eq(TimeUnit.HOURS));

        List<ConversationTurn> saved = objectMapper.readValue(jsonCaptor.getValue(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ConversationTurn.class));
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).content()).isEqualTo("Question?");
    }

    @Test
    void appendTurn_appendsToExistingHistory() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        List<ConversationTurn> existing = List.of(new ConversationTurn("user", "First"));
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(existing));

        conversationService.appendTurn("session-1", new ConversationTurn("assistant", "Second"));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), jsonCaptor.capture(), anyLong(), any());

        List<ConversationTurn> saved = objectMapper.readValue(jsonCaptor.getValue(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ConversationTurn.class));
        assertThat(saved).hasSize(2);
        assertThat(saved.get(1).content()).isEqualTo("Second");
    }

    @Test
    void deleteSession_deletesRedisKey() {
        conversationService.deleteSession("session-1");

        verify(redisTemplate).delete("chat:session:session-1");
    }
}
