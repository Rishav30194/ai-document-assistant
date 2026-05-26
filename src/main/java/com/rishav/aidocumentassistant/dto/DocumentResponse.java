package com.rishav.aidocumentassistant.dto;

import com.rishav.aidocumentassistant.model.DocumentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentResponse {
    private UUID id;
    private String name;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private DocumentStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;
}
