package com.hermes.domain.dto;

import com.hermes.domain.model.CreatorProfile;
import java.util.List;
import java.util.Map;

/**
 * Maps each search query to its list of discovered YouTube channels.
 * Preserves query-to-result association without merging or deduplication.
 */
public record QueryResultsMap(
        Map<String, List<CreatorProfile>> queryResults,
        int totalChannels,
        long timestamp) {

    /**
     * Creates a QueryResultsMap from the given query results.
     */
    public static QueryResultsMap of(Map<String, List<CreatorProfile>> results) {
        int total = results.values().stream()
                .mapToInt(List::size)
                .sum();
        return new QueryResultsMap(results, total, System.currentTimeMillis());
    }
}
