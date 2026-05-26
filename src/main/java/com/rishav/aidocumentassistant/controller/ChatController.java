package com.rishav.aidocumentassistant.controller;

import com.rishav.aidocumentassistant.dto.ChatRequest;
import com.rishav.aidocumentassistant.dto.ChatResponse;
import com.rishav.aidocumentassistant.model.ConversationTurn;
import com.rishav.aidocumentassistant.service.chat.ConversationService;
import com.rishav.aidocumentassistant.service.chat.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;
    private final ConversationService conversationService;

    @PostMapping("/{sessionId}")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragService.chat(sessionId, request.getMessage(), request.getDocumentId());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chatNewSession(@Valid @RequestBody ChatRequest request) {
        String sessionId = UUID.randomUUID().toString();
        ChatResponse response = ragService.chat(sessionId, request.getMessage(), request.getDocumentId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<ConversationTurn>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(conversationService.getHistory(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        conversationService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
