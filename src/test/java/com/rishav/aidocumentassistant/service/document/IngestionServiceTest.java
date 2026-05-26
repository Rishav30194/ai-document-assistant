package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.model.Document;
import com.rishav.aidocumentassistant.model.DocumentStatus;
import com.rishav.aidocumentassistant.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private IngestionService ingestionService;

    @TempDir
    Path tempDir;

    private UUID documentId;
    private Document testDocument;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        testDocument = Document.builder()
                .id(documentId)
                .name("Test Doc")
                .originalFileName("test.txt")
                .contentType("text/plain")
                .fileSize(100L)
                .filePath("/uploads/test.txt")
                .status(DocumentStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingest_storesChunks_andSetsStatusReady_onSuccess() throws IOException {
        Path file = createTextFile("Spring AI makes it easy to build AI-powered applications. " +
                "It provides abstractions over LLMs, vector stores, and embedding models. " +
                "This document covers the RAG pipeline in detail.");

        List<DocumentStatus> capturedStatuses = new java.util.ArrayList<>();
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            capturedStatuses.add(((Document) i.getArgument(0)).getStatus());
            return i.getArgument(0);
        });

        ingestionService.ingest(documentId, file.toString());

        verify(vectorStore).add(anyList());
        assertThat(capturedStatuses).containsExactly(DocumentStatus.PROCESSING, DocumentStatus.READY);
        assertThat(testDocument.getProcessedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingest_enrichesChunks_withDocumentIdMetadata() throws IOException {
        Path file = createTextFile("Document content for metadata enrichment test.");

        ingestionService.ingest(documentId, file.toString());

        ArgumentCaptor<List> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());

        List<org.springframework.ai.document.Document> chunks = chunksCaptor.getValue();
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry("documentId", documentId.toString())
        );
    }

    @Test
    void ingest_setsStatusToFailed_whenVectorStoreThrows() throws IOException {
        Path file = createTextFile("Content that will fail during vector store insertion.");
        doThrow(new RuntimeException("Vector store unavailable")).when(vectorStore).add(anyList());

        ingestionService.ingest(documentId, file.toString());

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, atLeastOnce()).save(captor.capture());
        Document lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void ingest_setsStatusToFailed_whenFileDoesNotExist() {
        String nonExistentPath = tempDir.resolve("ghost.txt").toString();

        ingestionService.ingest(documentId, nonExistentPath);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, atLeastOnce()).save(captor.capture());
        Document lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(DocumentStatus.FAILED);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void ingest_setsStatusToProcessing_beforeStoringChunks() throws IOException {
        Path file = createTextFile("Content to verify ordering of status updates.");

        List<DocumentStatus> capturedStatuses = new java.util.ArrayList<>();
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            capturedStatuses.add(((Document) i.getArgument(0)).getStatus());
            return i.getArgument(0);
        });

        ingestionService.ingest(documentId, file.toString());

        assertThat(capturedStatuses.get(0)).isEqualTo(DocumentStatus.PROCESSING);
    }

    private Path createTextFile(String content) throws IOException {
        Path file = tempDir.resolve("test-" + UUID.randomUUID() + ".txt");
        Files.writeString(file, content);
        return file;
    }
}
