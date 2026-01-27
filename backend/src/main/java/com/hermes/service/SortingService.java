package com.hermes.service;

import com.hermes.domain.entity.SearchSessionResult;
import com.hermes.domain.enums.SortKey;
import com.hermes.repository.SearchSessionResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for sorting session results by different metrics.
 * 
 * INVARIANTS (NON-NEGOTIABLE):
 * - Operates on precomputed, stored values ONLY
 * - No YouTube API calls
 * - No LLM calls
 * - No session recreation
 * - No ranking recomputation
 * - DB/cache read-only operations only
 * 
 * This service provides a pure read-time view over a fixed result set.
 */
@Service
public class SortingService {

    private static final Logger log = LoggerFactory.getLogger(SortingService.class);

    private final SearchSessionResultRepository resultRepository;

    public SortingService(SearchSessionResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /**
     * Retrieves paginated results sorted by the specified sort key.
     * 
     * @param sessionId The session ID to query
     * @param sortKey   The sort key (whitelisted only)
     * @param page      Page number (0-indexed)
     * @param pageSize  Results per page
     * @return Sorted, paginated results
     */
    public List<SearchSessionResult> getSortedResults(
            UUID sessionId,
            SortKey sortKey,
            int page,
            int pageSize) {

        Pageable pageable = PageRequest.of(page, pageSize);
        log.debug("[Sorting] Session={}, SortKey={}, Page={}, PageSize={}",
                sessionId, sortKey, page, pageSize);

        // Dispatch to appropriate sorted query based on SortKey
        return switch (sortKey) {
            case FINAL_SCORE -> resultRepository.findBySessionIdOrderByScoreDesc(sessionId, pageable);
            case RELEVANCE -> resultRepository.findBySessionIdOrderByRelevanceDesc(sessionId, pageable);
            case SUBSCRIBERS -> resultRepository.findBySessionIdOrderBySubscribersDesc(sessionId, pageable);
            case ENGAGEMENT -> resultRepository.findBySessionIdOrderByEngagementDesc(sessionId, pageable);
            case ACTIVITY -> resultRepository.findBySessionIdOrderByActivityDesc(sessionId, pageable);
            case COMPETITIVENESS -> resultRepository.findBySessionIdOrderByCompetitivenessDesc(sessionId, pageable);
        };
    }

    /**
     * Gets total count for a session (sort-agnostic).
     */
    public int getTotalCount(UUID sessionId) {
        return resultRepository.countBySessionId(sessionId);
    }

    /**
     * Calculates total pages for pagination.
     */
    public int getTotalPages(UUID sessionId, int pageSize) {
        int totalCount = getTotalCount(sessionId);
        return (int) Math.ceil((double) totalCount / pageSize);
    }

    // ===== FORBIDDEN OPERATIONS =====
    // The following operations are NEVER allowed in this service:
    // - YouTube API calls
    // - LLM calls
    // - Session creation
    // - Ranking recomputation
    // - Any external service calls
    //
    // This service is DB/cache read-only.
}
