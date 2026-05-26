package com.rishav.aidocumentassistant.controller;

import com.rishav.aidocumentassistant.dto.DocumentResponse;
import com.rishav.aidocumentassistant.service.document.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Upload and manage documents for RAG processing")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document", description = "Accepts PDF or DOCX. Triggers async ingestion pipeline.")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(file, name));
    }

    @GetMapping
    @Operation(summary = "List all documents")
    public ResponseEntity<List<DocumentResponse>> list() {
        return ResponseEntity.ok(documentService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    public ResponseEntity<DocumentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document and all its chunks")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
