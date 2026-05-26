package com.rishav.aidocumentassistant.service.chat;

import com.rishav.aidocumentassistant.dto.ChatResponse;
import com.rishav.aidocumentassistant.model.ConversationTurn;
import com.rishav.aidocumentassistant.service.document.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequest;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ConversationService conversationService;

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private RagService ragService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ragService, "topK", 3);
    }

    @Test
    void chat_returnsAnswerAndSavesHistory() {
        String sessionId = "sess-1";
        String userMsg = "What is Spring AI?";
        String expectedAnswer = "Spring AI is a framework.";

        org.springframework.ai.document.Document chunk = new org.springframework.ai.document.Document(
                "Spring AI provides abstractions.",
                Map.of("documentId", UUID.randomUUID().toString())
        );
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(chunk));
        when(conversationService.getHistory(sessionId)).thenReturn(List.of());
        stubChatClient(expectedAnswer);

        ChatResponse response = ragService.chat(sessionId, userMsg, null);

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getAnswer()).isEqualTo(expectedAnswer);
        assertThat(response.getSources()).hasSize(1);
    }

    @Test
    void chat_appendsBothTurnsToHistory() {
        String sessionId = "sess-2";
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of());
        when(conversationService.getHistory(sessionId)).thenReturn(List.of());
        stubChatClient("answer");

        ragService.chat(sessionId, "question", null);

        ArgumentCaptor<ConversationTurn> captor = ArgumentCaptor.forClass(ConversationTurn.class);
        verify(conversationService, times(2)).appendTurn(eq(sessionId), captor.capture());
        List<ConversationTurn> saved = captor.getAllValues();
        assertThat(saved.get(0).role()).isEqualTo("user");
        assertThat(saved.get(1).role()).isEqualTo("assistant");
    }

    @Test
    void chat_includesPriorHistoryInPrompt() {
        String sessionId = "sess-3";
        List<ConversationTurn> history = List.of(
                new ConversationTurn("user", "Previous question"),
                new ConversationTurn("assistant", "Previous answer")
        );
        when(conversationService.getHistory(sessionId)).thenReturn(history);
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of());
        stubChatClient("new answer");

        ChatResponse response = ragService.chat(sessionId, "Follow-up?", null);

        assertThat(response.getAnswer()).isEqualTo("new answer");
    }

    @Test
    void chat_resolvesDocumentName_fromDocumentService() {
        UUID docId = UUID.randomUUID();
        when(documentService.resolveDocumentName(docId.toString())).thenReturn("My Report");

        org.springframework.ai.document.Document chunk = new org.springframework.ai.document.Document(
                "Report content", Map.of("documentId", docId.toString())
        );
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(chunk));
        when(conversationService.getHistory(any())).thenReturn(List.of());
        stubChatClient("answer");

        ChatResponse response = ragService.chat("sess-4", "question", null);

        assertThat(response.getSources().get(0).getDocumentName()).isEqualTo("My Report");
    }

    @Test
    void chat_truncatesLongExcerpts() {
        String longText = "A".repeat(300);
        org.springframework.ai.document.Document chunk = new org.springframework.ai.document.Document(
                longText, Map.of("documentId", UUID.randomUUID().toString())
        );
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(chunk));
        when(conversationService.getHistory(any())).thenReturn(List.of());
        stubChatClient("answer");

        ChatResponse response = ragService.chat("sess-5", "question", null);

        assertThat(response.getSources().get(0).getExcerpt()).hasSizeLessThanOrEqualTo(201);
        assertThat(response.getSources().get(0).getExcerpt()).endsWith("…");
    }

    @SuppressWarnings("unchecked")
    private void stubChatClient(String answer) {
        when(chatClient.prompt()).thenReturn(chatClientRequest);
        when(chatClientRequest.system(anyString())).thenReturn(chatClientRequest);
        when(chatClientRequest.messages(any(List.class))).thenReturn(chatClientRequest);
        when(chatClientRequest.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(answer);
    }
}
