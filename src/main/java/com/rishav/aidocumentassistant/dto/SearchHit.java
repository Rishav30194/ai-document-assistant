package com.rishav.aidocumentassistant.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchHit {
    private String documentId;
    private String documentName;
    private String excerpt;
    private Double score;
}
