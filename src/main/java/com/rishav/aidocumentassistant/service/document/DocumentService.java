package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.dto.DocumentResponse;
import com.rishav.aidocumentassistant.exception.DocumentNotFoundException;
import com.rishav.aidocumentassistant.exception.UnsupportedFileTypeException;
import com.rishav.aidocumentassistant.model.Document;
import com.rishav.aidocumentassistant.model.DocumentStatus;
import com.rishav.aidocumentassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ACCEPTED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final IngestionService ingestionService;

    @Transactional
    public DocumentResponse upload(MultipartFile file, String name) {
        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_TYPES.contains(contentType)) {
            throw new UnsupportedFileTypeException(contentType);
        }

        String filePath = fileStorageService.store(file);

        Document document = Document.builder()
                .name(name != null && !name.isBlank() ? name : file.getOriginalFilename())
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .filePath(filePath)
                .status(DocumentStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .build();

        Document saved = documentRepository.save(document);

        // Trigger ingestion after commit — ensures the document is visible to the async thread
        UUID savedId = saved.getId();
        String savedPath = saved.getFilePath();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ingestionService.ingest(savedId, savedPath);
                }
            });
        } else {
            ingestionService.ingest(savedId, savedPath);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> findAll() {
        return documentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(UUID id) {
        return documentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        fileStorageService.delete(document.getFilePath());
        documentRepository.delete(document);
    }

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .name(doc.getName())
                .originalFileName(doc.getOriginalFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .uploadedAt(doc.getUploadedAt())
                .processedAt(doc.getProcessedAt())
                .build();
    }
}
