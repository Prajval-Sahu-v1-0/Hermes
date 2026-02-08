package com.hermes.service.filter;

import com.hermes.domain.dto.FilterCriteria;
import com.hermes.domain.entity.SearchSessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for applying multi-select filters to session results.
 * 
 * @deprecated Use
 *             {@link com.hermes.repository.specification.SearchSessionResultSpecification}
 *             for database-level filtering instead. This service performs
 *             in-memory filtering
 *             which is less efficient and has been superseded by JPA
 *             Specification-based
 *             filtering in SearchSessionService.paginateFiltered().
 * 
 *             This class remains for backward compatibility with any code that
 *             still uses
 *             in-memory filtering. New code MUST NOT use this service.
 * 
 *             FILTERING RULES:
 *             - OR logic within the same category (pass if ANY selected bucket
 *             matches)
 *             - AND logic across categories (must pass ALL active category
 *             filters)
 * 
 *             INVARIANTS (NON-NEGOTIABLE):
 *             - Operates on precomputed, stored statistics ONLY
 *             - NO YouTube API calls
 *             - NO LLM calls
 *             - NO recomputation
 *             - NO re-ranking
 *             - Session-scoped, read-only
 * 
 *             EXECUTION ORDER:
 *             1. Resolve SearchSession
 *             2. Apply multi-select filters (this service)
 *             3. Apply sorting
 *             4. Apply pagination
 */
@Deprecated(since = "1.1", forRemoval = true)
@Service
public class FilterService {

    private static final Logger log = LoggerFactory.getLogger(FilterService.class);

    /**
     * Applies multi-select filters to a list of session results.
     * 
     * @param results  Unfiltered results from the session
     * @param criteria Multi-select filter criteria
     * @return Filtered results (may be smaller than input)
     */
    public List<SearchSessionResult> applyFilters(
            List<SearchSessionResult> results,
            FilterCriteria criteria) {

        if (criteria == null || criteria.isEmpty()) {
            log.debug("[Filter] No filters active, returning all {} results", results.size());
            return results;
        }

        log.debug("[Filter] Applying {} filter categories to {} results",
                criteria.activeFilterCount(), results.size());

        List<SearchSessionResult> filtered = results.stream()
                .filter(r -> passesAllFilters(r, criteria))
                .collect(Collectors.toList());

        log.debug("[Filter] Filtered to {} results", filtered.size());
        return filtered;
    }

    /**
     * Checks if a result passes ALL active filter categories (AND logic).
     */
    private boolean passesAllFilters(SearchSessionResult result, FilterCriteria criteria) {
        // AND logic: must pass ALL active filters

        // Audience filter (OR within buckets)
        if (!passesAudienceFilter(result, criteria.audience())) {
            return false;
        }

        // Engagement filter (OR within buckets)
        if (!passesEngagementFilter(result, criteria.engagement())) {
            return false;
        }

        // Competitiveness filter (OR within buckets)
        if (!passesCompetitivenessFilter(result, criteria.competitiveness())) {
            return false;
        }

        // Activity filter (OR within buckets)
        if (!passesActivityFilter(result, criteria.activity())) {
            return false;
        }

        // Genre filter (OR within genres - check labels)
        if (!passesGenreFilter(result, criteria.genres())) {
            return false;
        }

        return true;
    }

    // ===== INDIVIDUAL FILTER METHODS =====
    // Each returns true if filter is empty OR result matches ANY selected bucket

    private boolean passesAudienceFilter(SearchSessionResult result, Set<String> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return true; // No filter = pass
        }
        return BucketMapper.matchesAnyAudienceBucket(result.getAudienceFit(), buckets);
    }

    private boolean passesEngagementFilter(SearchSessionResult result, Set<String> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return true;
        }
        return BucketMapper.matchesAnyEngagementBucket(result.getEngagementQuality(), buckets);
    }

    private boolean passesCompetitivenessFilter(SearchSessionResult result, Set<String> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return true;
        }
        return BucketMapper.matchesAnyCompetitivenessBucket(result.getCompetitivenessScore(), buckets);
    }

    private boolean passesActivityFilter(SearchSessionResult result, Set<String> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return true;
        }
        return BucketMapper.matchesAnyActivityBucket(result.getActivityConsistency(), buckets);
    }

    private boolean passesGenreFilter(SearchSessionResult result, Set<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return true;
        }
        // Check if any label matches any selected genre (case-insensitive)
        String[] labels = result.getLabels();
        if (labels == null || labels.length == 0) {
            return false; // No labels = no match
        }
        Set<String> lowerGenres = genres.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return Arrays.stream(labels)
                .map(String::toLowerCase)
                .anyMatch(lowerGenres::contains);
    }

    // ===== FORBIDDEN OPERATIONS =====
    // This service is PURE READ-ONLY.
    // The following are NEVER allowed:
    // - YouTube API calls
    // - LLM calls
    // - Session creation
    // - Score recomputation
    // - Any external service calls
}
