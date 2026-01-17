package com.hermes.domain.dto;

import com.hermes.domain.grading.GradedCreator;
import java.util.List;

/**
 * Paginated result set containing ranked, deduplicated creators.
 * Page index is 0-based.
 */
public record PaginatedResult(
        List<GradedCreator> creators,
        int page,
        int pageSize,
        int totalResults,
        int totalPages) {
    /**
     * Creates an empty result.
     */
    public static PaginatedResult empty(int page, int pageSize) {
        return new PaginatedResult(List.of(), page, pageSize, 0, 0);
    }

    /**
     * Returns true if there are more pages after this one.
     */
    public boolean hasNextPage() {
        return page < totalPages - 1;
    }

    /**
     * Returns true if there are pages before this one.
     */
    public boolean hasPreviousPage() {
        return page > 0;
    }
}
