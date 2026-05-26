package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.model.DocumentStatus;
import com.rishav.aidocumentassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Async("ingestionExecutor")
    public void ingest(UUID documentId, String filePath) {
        log.info("Ingestion started — documentId={}", documentId);
        try {
            updateStatus(documentId, DocumentStatus.PROCESSING, null);

            // 1. Parse — Tika handles PDF, DOCX, and other formats
            TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
            List<Document> parsed = reader.get();

            if (parsed.isEmpty()) {
                log.warn("No text extracted from document {} — marking as FAILED", documentId);
                updateStatus(documentId, DocumentStatus.FAILED, null);
                return;
            }

            // 2. Chunk — split parsed text into overlapping token windows
            List<Document> chunks = new TokenTextSplitter().apply(parsed);

            // 3. Enrich — tag every chunk with documentId for filtered retrieval later
            chunks.forEach(chunk -> chunk.getMetadata().put("documentId", documentId.toString()));

            // 4. Embed + store — VectorStore calls EmbeddingModel (OpenAI) internally
            vectorStore.add(chunks);

            updateStatus(documentId, DocumentStatus.READY, LocalDateTime.now());
            log.info("Ingestion complete — documentId={}, chunks={}", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed — documentId={}", documentId, e);
            updateStatus(documentId, DocumentStatus.FAILED, null);
        }
    }

    public void deleteChunks(UUID documentId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'documentId' = ?",
                documentId.toString());
        log.info("Deleted {} chunk(s) from vector store — documentId={}", deleted, documentId);
    }

    private void updateStatus(UUID id, DocumentStatus status, LocalDateTime processedAt) {
        documentRepository.findById(id).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setProcessedAt(processedAt);
            documentRepository.save(doc);
        });
    }
}
