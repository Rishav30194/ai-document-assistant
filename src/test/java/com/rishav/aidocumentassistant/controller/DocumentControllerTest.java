package com.rishav.aidocumentassistant.controller;

import com.rishav.aidocumentassistant.dto.DocumentResponse;
import com.rishav.aidocumentassistant.exception.DocumentNotFoundException;
import com.rishav.aidocumentassistant.model.DocumentStatus;
import com.rishav.aidocumentassistant.service.document.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    void uploadDocument_returns201_withDocumentResponse() throws Exception {
        UUID id = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        when(documentService.upload(any(), eq("My Report"))).thenReturn(buildResponse(id, "My Report"));

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("name", "My Report"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("My Report"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.uploadedAt").exists());
    }

    @Test
    void uploadDocument_withoutName_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes()
        );
        when(documentService.upload(any(), eq(null))).thenReturn(buildResponse(UUID.randomUUID(), "report.pdf"));

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("report.pdf"));
    }

    @Test
    void listDocuments_returns200_withDocumentList() throws Exception {
        List<DocumentResponse> docs = List.of(
                buildResponse(UUID.randomUUID(), "Doc A"),
                buildResponse(UUID.randomUUID(), "Doc B")
        );
        when(documentService.findAll()).thenReturn(docs);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Doc A"))
                .andExpect(jsonPath("$[1].name").value("Doc B"));
    }

    @Test
    void listDocuments_returns200_withEmptyList() throws Exception {
        when(documentService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getDocumentById_returns200_whenFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.findById(id)).thenReturn(buildResponse(id, "My Doc"));

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("My Doc"));
    }

    @Test
    void getDocumentById_returns404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.findById(id)).thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Document not found: " + id));
    }

    @Test
    void deleteDocument_returns204_whenFound() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(documentService).delete(id);

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNoContent());

        verify(documentService).delete(id);
    }

    @Test
    void deleteDocument_returns404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DocumentNotFoundException(id)).when(documentService).delete(id);

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private DocumentResponse buildResponse(UUID id, String name) {
        return DocumentResponse.builder()
                .id(id)
                .name(name)
                .originalFileName("report.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .status(DocumentStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .build();
    }
}
