package com.rishav.aidocumentassistant.service.search;

import com.rishav.aidocumentassistant.dto.SearchHit;
import com.rishav.aidocumentassistant.dto.SearchQuery;
import com.rishav.aidocumentassistant.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final VectorStore vectorStore;
    private final DocumentService documentService;

    public List<SearchHit> search(SearchQuery query) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query.getQuery())
                .topK(query.getTopK());

        if (query.getDocumentId() != null && !query.getDocumentId().isBlank()) {
            FilterExpressionBuilder fb = new FilterExpressionBuilder();
            builder.filterExpression(fb.eq("documentId", query.getDocumentId()).build());
        }

        return vectorStore.similaritySearch(builder.build())
                .stream()
                .map(this::toHit)
                .toList();
    }

    private SearchHit toHit(Document chunk) {
        String docId = (String) chunk.getMetadata().get("documentId");
        return SearchHit.builder()
                .documentId(docId)
                .documentName(documentService.resolveDocumentName(docId))
                .excerpt(chunk.getText())
                .score(chunk.getScore())
                .build();
    }
}
