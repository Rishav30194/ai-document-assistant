package com.rishav.aidocumentassistant.service.search;

import com.rishav.aidocumentassistant.dto.SearchHit;
import com.rishav.aidocumentassistant.dto.SearchQuery;
import com.rishav.aidocumentassistant.service.document.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private SearchService searchService;

    @Test
    void search_returnsHitsWithScoreAndDocumentName() {
        UUID docId = UUID.randomUUID();
        when(documentService.resolveDocumentName(docId.toString())).thenReturn("Finance Report");

        org.springframework.ai.document.Document chunk = org.springframework.ai.document.Document.builder()
                .text("Revenue increased by 20%.")
                .metadata("documentId", docId.toString())
                .score(0.92)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        SearchQuery query = buildQuery("revenue growth", null, 5);
        List<SearchHit> hits = searchService.search(query);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getDocumentName()).isEqualTo("Finance Report");
        assertThat(hits.get(0).getExcerpt()).isEqualTo("Revenue increased by 20%.");
        assertThat(hits.get(0).getScore()).isEqualTo(0.92);
    }

    @Test
    void search_passesTopKToVectorStore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        searchService.search(buildQuery("query", null, 3));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(3);
    }

    @Test
    void search_appliesDocumentIdFilter_whenProvided() {
        String docId = UUID.randomUUID().toString();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        searchService.search(buildQuery("query", docId, 5));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).isNotNull();
    }

    @Test
    void search_noFilterApplied_whenDocumentIdIsNull() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        searchService.search(buildQuery("query", null, 5));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).isNull();
    }

    @Test
    void search_returnsEmptyList_whenNoChunksMatch() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<SearchHit> hits = searchService.search(buildQuery("obscure query", null, 5));

        assertThat(hits).isEmpty();
        verifyNoInteractions(documentService);
    }

    @Test
    void search_handlesUnknownDocumentId_gracefully() {
        String unknownId = UUID.randomUUID().toString();
        when(documentService.resolveDocumentName(unknownId)).thenReturn("Unknown");

        org.springframework.ai.document.Document chunk = org.springframework.ai.document.Document.builder()
                .text("Some content.")
                .metadata("documentId", unknownId)
                .score(0.80)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        List<SearchHit> hits = searchService.search(buildQuery("query", null, 5));

        assertThat(hits.get(0).getDocumentName()).isEqualTo("Unknown");
    }

    private SearchQuery buildQuery(String query, String documentId, int topK) {
        SearchQuery q = new SearchQuery();
        ReflectionTestUtils.setField(q, "query", query);
        ReflectionTestUtils.setField(q, "documentId", documentId);
        ReflectionTestUtils.setField(q, "topK", topK);
        return q;
    }
}
