package com.rishav.aidocumentassistant.controller;

import com.rishav.aidocumentassistant.dto.SearchHit;
import com.rishav.aidocumentassistant.dto.SearchQuery;
import com.rishav.aidocumentassistant.service.search.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    public ResponseEntity<List<SearchHit>> search(@Valid @RequestBody SearchQuery query) {
        return ResponseEntity.ok(searchService.search(query));
    }
}
