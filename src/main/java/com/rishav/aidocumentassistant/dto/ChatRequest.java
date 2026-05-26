package com.rishav.aidocumentassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequest {

    @NotBlank(message = "message must not be blank")
    private String message;

    private String documentId;
}
