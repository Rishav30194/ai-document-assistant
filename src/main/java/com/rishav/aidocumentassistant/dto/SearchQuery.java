package com.rishav.aidocumentassistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchQuery {

    @NotBlank(message = "query must not be blank")
    private String query;

    private String documentId;

    @Min(1) @Max(20)
    private int topK = 5;
}
