package com.rishav.aidocumentassistant.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SourceReference {
    private String documentId;
    private String documentName;
    private String excerpt;
}
