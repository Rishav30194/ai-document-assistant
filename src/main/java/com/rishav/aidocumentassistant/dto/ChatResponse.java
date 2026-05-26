package com.rishav.aidocumentassistant.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatResponse {
    private String sessionId;
    private String answer;
    private List<SourceReference> sources;
}
