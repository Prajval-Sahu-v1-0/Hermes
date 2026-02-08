package com.hermes.repository.specification;

import com.hermes.domain.dto.FilterCriteria;
import com.hermes.domain.entity.SearchSessionResult;
import com.hermes.service.filter.BucketMapper;
import com.hermes.service.filter.BucketMapper.ScoreRange;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JPA Specification for filtering SearchSessionResult.
 * 
 * FILTER LOGIC:
 * - OR within the same category (any selected bucket matches)
 * - AND across different categories (must pass all active filters)
 * 
 * INVARIANTS (NON-NEGOTIABLE):
 * - All bucket boundaries delegate to BucketMapper (single source of truth)
 * - No in-memory filtering - all predicates execute in database
 * - Specification is composable and reusable for count queries
 * 
 * NOTE: ACTIVITY SortKey semantic mismatch is intentionally not addressed
 * in this change set. See filter_system_analysis.md for details.
 */
public class SearchSessionResultSpecification {

    private SearchSessionResultSpecification() {
        // Static factory class
    }

    /**
     * Creates a complete Specification from FilterCriteria.
     * Combines all active filters with AND logic.
     * 
     * @param sessionId Session ID constraint (required)
     * @param filters   Filter criteria (nullable)
     * @return Combined Specification for database query
     */
    public static Specification<SearchSessionResult> fromCriteria(UUID sessionId, FilterCriteria filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Base constraint: session ID (always required)
            predicates.add(cb.equal(root.get("sessionId"), sessionId));

            if (filters == null || filters.isEmpty()) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Audience filter (OR within buckets)
            if (filters.audience() != null && !filters.audience().isEmpty()) {
                predicates.add(audiencePredicate(root, cb, filters.audience()));
            }

            // Engagement filter (OR within buckets)
            if (filters.engagement() != null && !filters.engagement().isEmpty()) {
                predicates.add(engagementPredicate(root, cb, filters.engagement()));
            }

            // Competitiveness filter (OR within buckets)
            if (filters.competitiveness() != null && !filters.competitiveness().isEmpty()) {
                predicates.add(competitivenessPredicate(root, cb, filters.competitiveness()));
            }

            // Activity filter (OR within buckets)
            if (filters.activity() != null && !filters.activity().isEmpty()) {
                predicates.add(activityPredicate(root, cb, filters.activity()));
            }

            // Genre filter (OR within labels - uses LIKE for array search)
            if (filters.genres() != null && !filters.genres().isEmpty()) {
                predicates.add(genrePredicate(root, cb, filters.genres()));
            }

            // Platform filter (STUB - platform is on session level, not result)
            // TODO: To enable per-result platform filtering, add platform column to
            // SearchSessionResult
            // For now, platforms are uniform within a session, so this filter is a no-op
            if (filters.platform() != null && !filters.platform().isEmpty()) {
                // Log that filter exists but isn't applied at result level
                // Actual platform filtering happens at session creation time
            }

            // Combine all predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ===== BUCKET PREDICATES (delegating to BucketMapper) =====

    /**
     * Creates OR predicate for audience buckets.
     * Delegates to BucketMapper for bucket boundaries.
     */
    private static Predicate audiencePredicate(
            Root<SearchSessionResult> root, CriteriaBuilder cb, Set<String> buckets) {
        List<Predicate> orPredicates = new ArrayList<>();
        for (String bucket : buckets) {
            ScoreRange range = BucketMapper.audienceRange(bucket);
            orPredicates.add(rangePredicate(root, cb, "audienceFit", range));
        }
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Creates OR predicate for engagement buckets.
     */
    private static Predicate engagementPredicate(
            Root<SearchSessionResult> root, CriteriaBuilder cb, Set<String> buckets) {
        List<Predicate> orPredicates = new ArrayList<>();
        for (String bucket : buckets) {
            ScoreRange range = BucketMapper.engagementRange(bucket);
            orPredicates.add(rangePredicate(root, cb, "engagementQuality", range));
        }
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Creates OR predicate for competitiveness buckets.
     */
    private static Predicate competitivenessPredicate(
            Root<SearchSessionResult> root, CriteriaBuilder cb, Set<String> buckets) {
        List<Predicate> orPredicates = new ArrayList<>();
        for (String bucket : buckets) {
            ScoreRange range = BucketMapper.competitivenessRange(bucket);
            orPredicates.add(rangePredicate(root, cb, "competitivenessScore", range));
        }
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Creates OR predicate for activity buckets.
     */
    private static Predicate activityPredicate(
            Root<SearchSessionResult> root, CriteriaBuilder cb, Set<String> buckets) {
        List<Predicate> orPredicates = new ArrayList<>();
        for (String bucket : buckets) {
            ScoreRange range = BucketMapper.activityRange(bucket);
            orPredicates.add(rangePredicate(root, cb, "activityConsistency", range));
        }
        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Creates OR predicate for genre label matching.
     * Uses PostgreSQL array containment or LIKE for compatibility.
     */
    private static Predicate genrePredicate(
            Root<SearchSessionResult> root, CriteriaBuilder cb, Set<String> genres) {
        // For PostgreSQL TEXT[] column, use native array contains
        // Fallback to LOWER + LIKE for broader compatibility
        List<Predicate> orPredicates = new ArrayList<>();

        for (String genre : genres) {
            // Convert to lowercase for case-insensitive match
            // Uses function call to handle array-to-string conversion
            Expression<String> labelsAsString = cb.function(
                    "array_to_string",
                    String.class,
                    root.get("labels"),
                    cb.literal(","));

            orPredicates.add(cb.like(
                    cb.lower(labelsAsString),
                    "%" + genre.toLowerCase() + "%"));
        }

        return cb.or(orPredicates.toArray(new Predicate[0]));
    }

    // ===== UTILITY METHODS =====

    /**
     * Creates a range predicate: min <= value < max
     * Delegates boundary checks to BucketMapper.ScoreRange.
     */
    private static Predicate rangePredicate(
            Root<SearchSessionResult> root, CriteriaBuilder cb,
            String fieldName, ScoreRange range) {
        Path<Double> field = root.get(fieldName);
        return cb.and(
                cb.greaterThanOrEqualTo(field, range.min()),
                cb.lessThan(field, range.max()));
    }
}
