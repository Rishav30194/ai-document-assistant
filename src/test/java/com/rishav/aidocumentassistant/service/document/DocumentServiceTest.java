package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.dto.DocumentResponse;
import com.rishav.aidocumentassistant.exception.DocumentNotFoundException;
import com.rishav.aidocumentassistant.model.Document;
import com.rishav.aidocumentassistant.model.DocumentStatus;
import com.rishav.aidocumentassistant.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private IngestionService ingestionService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void upload_savesDocumentWithProvidedName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        when(fileStorageService.store(file)).thenReturn("/uploads/report.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = documentService.upload(file, "My Report");

        assertThat(response.getName()).isEqualTo("My Report");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.getOriginalFileName()).isEqualTo("report.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void upload_usesOriginalFilename_whenNameIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        when(fileStorageService.store(file)).thenReturn("/uploads/report.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = documentService.upload(file, null);

        assertThat(response.getName()).isEqualTo("report.pdf");
    }

    @Test
    void upload_usesOriginalFilename_whenNameIsBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        when(fileStorageService.store(file)).thenReturn("/uploads/report.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = documentService.upload(file, "   ");

        assertThat(response.getName()).isEqualTo("report.pdf");
    }

    @Test
    void upload_setsStatusToPending_andCapturesCorrectFields() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.pdf", "application/pdf", "content".getBytes()
        );
        when(fileStorageService.store(file)).thenReturn("/uploads/notes.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);

        documentService.upload(file, "Notes");

        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(saved.getFilePath()).isEqualTo("/uploads/notes.pdf");
        assertThat(saved.getUploadedAt()).isNotNull();
        assertThat(saved.getProcessedAt()).isNull();
    }

    @Test
    void upload_triggersIngestion_afterSave() {
        UUID generatedId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        when(fileStorageService.store(file)).thenReturn("/uploads/report.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> {
            Document doc = i.getArgument(0);
            doc.setId(generatedId);
            return doc;
        });

        documentService.upload(file, "Report");

        verify(ingestionService).ingest(eq(generatedId), eq("/uploads/report.pdf"));
    }

    @Test
    void findAll_returnsAllDocuments() {
        List<Document> docs = List.of(
                buildDocument(UUID.randomUUID(), "Doc A"),
                buildDocument(UUID.randomUUID(), "Doc B")
        );
        when(documentRepository.findAll()).thenReturn(docs);

        List<DocumentResponse> result = documentService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DocumentResponse::getName).containsExactly("Doc A", "Doc B");
    }

    @Test
    void findAll_returnsEmptyList_whenNoneExist() {
        when(documentRepository.findAll()).thenReturn(List.of());

        assertThat(documentService.findAll()).isEmpty();
    }

    @Test
    void findById_returnsDocument_whenFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.of(buildDocument(id, "My Doc")));

        DocumentResponse response = documentService.findById(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("My Doc");
    }

    @Test
    void findById_throwsDocumentNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(id))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void delete_deletesChunksFileAndDocument_whenFound() {
        UUID id = UUID.randomUUID();
        Document doc = buildDocument(id, "To Delete");
        when(documentRepository.findById(id)).thenReturn(Optional.of(doc));

        documentService.delete(id);

        verify(ingestionService).deleteChunks(id);
        verify(fileStorageService).delete(doc.getFilePath());
        verify(documentRepository).delete(doc);
    }

    @Test
    void delete_throwsDocumentNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.delete(id))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(id.toString());

        verifyNoInteractions(ingestionService);
        verifyNoInteractions(fileStorageService);
    }

    private Document buildDocument(UUID id, String name) {
        return Document.builder()
                .id(id)
                .name(name)
                .originalFileName("file.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .filePath("/uploads/file.pdf")
                .status(DocumentStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .build();
    }
}
