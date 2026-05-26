package com.rishav.aidocumentassistant.service.chat;

import com.rishav.aidocumentassistant.dto.ChatResponse;
import com.rishav.aidocumentassistant.dto.SourceReference;
import com.rishav.aidocumentassistant.model.ConversationTurn;
import com.rishav.aidocumentassistant.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ConversationService conversationService;
    private final DocumentService documentService;

    @Value("${chat.rag.top-k:5}")
    private int topK;

    private String systemPromptTemplate;

    @PostConstruct
    void init() {
        try {
            systemPromptTemplate = new ClassPathResource("prompts/rag-system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load system prompt, using fallback", e);
            systemPromptTemplate = "Answer questions using the provided document excerpts.\n\nDocument excerpts:\n{context}";
        }
    }

    public ChatResponse chat(String sessionId, String userMessage, String documentId) {
        // 1. Retrieve relevant chunks
        List<Document> chunks = retrieveChunks(userMessage, documentId);

        // 2. Build context string from chunks
        String context = buildContext(chunks);

        // 3. Inject context into the cached system prompt template
        String systemPrompt = systemPromptTemplate.replace("{context}", context);

        // 4. Build message history for multi-turn
        List<ConversationTurn> history = conversationService.getHistory(sessionId);
        List<Message> messages = buildMessages(history, userMessage);

        // 5. Call Claude
        String answer = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .call()
                .content();

        // 6. Persist both turns
        conversationService.appendTurn(sessionId, new ConversationTurn("user", userMessage));
        conversationService.appendTurn(sessionId, new ConversationTurn("assistant", answer));

        // 7. Build source references
        List<SourceReference> sources = buildSources(chunks);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .answer(answer)
                .sources(sources)
                .build();
    }

    private List<Document> retrieveChunks(String query, String documentId) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (documentId != null && !documentId.isBlank()) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.eq("documentId", documentId).build());
        }

        return vectorStore.similaritySearch(builder.build());
    }

    private String buildContext(List<Document> chunks) {
        if (chunks.isEmpty()) return "No relevant document content found.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(chunks.get(i).getText()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private List<Message> buildMessages(List<ConversationTurn> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        for (ConversationTurn turn : history) {
            if ("user".equals(turn.role())) {
                messages.add(new UserMessage(turn.content()));
            } else {
                messages.add(new AssistantMessage(turn.content()));
            }
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private List<SourceReference> buildSources(List<Document> chunks) {
        return chunks.stream().map(chunk -> {
            String docId = (String) chunk.getMetadata().get("documentId");
            String excerpt = chunk.getText().length() > 200
                    ? chunk.getText().substring(0, 200) + "…"
                    : chunk.getText();
            return SourceReference.builder()
                    .documentId(docId)
                    .documentName(documentService.resolveDocumentName(docId))
                    .excerpt(excerpt)
                    .build();
        }).toList();
    }


}
