package com.rishav.aidocumentassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishav.aidocumentassistant.dto.ChatResponse;
import com.rishav.aidocumentassistant.dto.SourceReference;
import com.rishav.aidocumentassistant.model.ConversationTurn;
import com.rishav.aidocumentassistant.service.chat.ConversationService;
import com.rishav.aidocumentassistant.service.chat.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void chat_withSessionId_returns200WithAnswer() throws Exception {
        ChatResponse response = ChatResponse.builder()
                .sessionId("sess-1")
                .answer("The answer is 42.")
                .sources(List.of())
                .build();
        when(ragService.chat(eq("sess-1"), eq("What is the answer?"), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/chat/sess-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "What is the answer?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.answer").value("The answer is 42."));
    }

    @Test
    void chat_withoutSessionId_generatesNewSessionAndReturns200() throws Exception {
        ChatResponse response = ChatResponse.builder()
                .sessionId("generated-id")
                .answer("Hello!")
                .sources(List.of())
                .build();
        when(ragService.chat(anyString(), anyString(), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Hi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hello!"));
    }

    @Test
    void chat_withDocumentId_passesItToRagService() throws Exception {
        ChatResponse response = ChatResponse.builder()
                .sessionId("sess-2")
                .answer("Filtered answer.")
                .sources(List.of())
                .build();
        when(ragService.chat(eq("sess-2"), eq("Question?"), eq("doc-uuid"))).thenReturn(response);

        mockMvc.perform(post("/api/chat/sess-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "Question?", "documentId", "doc-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Filtered answer."));
    }

    @Test
    void chat_returnsSourceReferences() throws Exception {
        SourceReference source = SourceReference.builder()
                .documentId("doc-1")
                .documentName("Annual Report")
                .excerpt("Revenue grew 20%...")
                .build();
        ChatResponse response = ChatResponse.builder()
                .sessionId("sess-3")
                .answer("Revenue grew.")
                .sources(List.of(source))
                .build();
        when(ragService.chat(anyString(), anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/chat/sess-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Revenue?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].documentName").value("Annual Report"))
                .andExpect(jsonPath("$.sources[0].excerpt").value("Revenue grew 20%..."));
    }

    @Test
    void chat_returns400_whenMessageIsBlank() throws Exception {
        mockMvc.perform(post("/api/chat/sess-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_returnsConversationTurns() throws Exception {
        List<ConversationTurn> turns = List.of(
                new ConversationTurn("user", "Hello"),
                new ConversationTurn("assistant", "Hi there")
        );
        when(conversationService.getHistory("sess-5")).thenReturn(turns);

        mockMvc.perform(get("/api/chat/sess-5/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void deleteSession_returns204() throws Exception {
        mockMvc.perform(delete("/api/chat/sess-6"))
                .andExpect(status().isNoContent());

        verify(conversationService).deleteSession("sess-6");
    }
}
