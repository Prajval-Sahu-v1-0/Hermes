package com.hermes.controller;

import com.hermes.domain.dto.SearchRequest;
import com.hermes.domain.dto.SearchResult;
import com.hermes.domain.model.CreatorProfile;
import com.hermes.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // For local development flexibility
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        SearchResult searchResult = searchService.performSearch(request);

        // Phase One: Query info
        var queryInfo = searchResult.queryInfo();

        // Phase Two: Channel results (query-to-profiles mapping)
        var channelResults = searchResult.channelResults();

        // Pagination Logic for creators (currently empty, for future use)
        List<CreatorProfile> allResults = searchResult.creators();
        int pageSize = 5;
        int page = Math.max(0, request.page());
        int fromIndex = page * pageSize;

        List<CreatorProfile> paginatedResults;
        if (fromIndex >= allResults.size()) {
            paginatedResults = List.of();
        } else {
            int toIndex = Math.min(fromIndex + pageSize, allResults.size());
            paginatedResults = allResults.subList(fromIndex, toIndex);
        }

        return ResponseEntity.ok(Map.of(
                "results", paginatedResults,
                "totalResults", allResults.size(),
                "currentPage", page,
                "totalPages", (int) Math.ceil((double) allResults.size() / pageSize),
                "queryInfo", queryInfo,
                "channelResults", channelResults != null ? channelResults : Map.of()));
    }
}
