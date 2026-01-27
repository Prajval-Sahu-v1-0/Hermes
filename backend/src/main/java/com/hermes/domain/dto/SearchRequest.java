package com.hermes.domain.dto;

import com.hermes.domain.model.CreatorProfile;
import java.util.List;
import java.util.Map;

public record SearchRequest(
        String platform,
        String genre,
        Map<String, String> filters, // e.g., {"audience": "large", "engagement": "high"}
        int page,
        int pageSize // Number of results per page (default 10 if 0 or negative)
) {
}

record SearchResponse(
        List<CreatorProfile> results,
        long totalResults,
        int currentPage,
        int totalPages) {
}
