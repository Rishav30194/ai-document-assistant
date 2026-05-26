package com.rishav.aidocumentassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rishav.aidocumentassistant.dto.SearchHit;
import com.rishav.aidocumentassistant.service.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SearchService searchService;

    @Test
    void search_returns200WithHits() throws Exception {
        SearchHit hit = SearchHit.builder()
                .documentId("doc-1")
                .documentName("Annual Report")
                .excerpt("Revenue grew 20%.")
                .score(0.91)
                .build();
        when(searchService.search(any())).thenReturn(List.of(hit));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "revenue"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentName").value("Annual Report"))
                .andExpect(jsonPath("$[0].excerpt").value("Revenue grew 20%."))
                .andExpect(jsonPath("$[0].score").value(0.91));
    }

    @Test
    void search_returns400_whenQueryIsBlank() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_returns400_whenTopKExceedsMax() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("query", "something", "topK", 25))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_returnsEmptyList_whenNoHits() throws Exception {
        when(searchService.search(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("query", "obscure topic"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void search_acceptsOptionalDocumentIdFilter() throws Exception {
        when(searchService.search(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("query", "topic", "documentId", "some-doc-id"))))
                .andExpect(status().isOk());
    }
}
