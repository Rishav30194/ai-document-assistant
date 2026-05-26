package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.dto.DocumentResponse;
import com.rishav.aidocumentassistant.exception.DocumentNotFoundException;
import com.rishav.aidocumentassistant.model.Document;
import com.rishav.aidocumentassistant.model.DocumentStatus;
import com.rishav.aidocumentassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public DocumentResponse upload(MultipartFile file, String name) {
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

        return toResponse(documentRepository.save(document));
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
