package com.hermes.repository.specification;

import com.hermes.domain.dto.FilterCriteria;
import com.hermes.domain.entity.SearchSession;
import com.hermes.domain.entity.SearchSessionResult;
import com.hermes.repository.SearchSessionRepository;
import com.hermes.repository.SearchSessionResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SearchSessionResultSpecification.
 * Verifies database-level filtering with:
 * - OR logic within filter categories
 * - AND logic across filter categories
 * - Correct delegation to BucketMapper boundaries
 */
@DataJpaTest
@ActiveProfiles("test")
class SearchSessionResultSpecificationTest {

    @Autowired
    private SearchSessionResultRepository resultRepository;

    @Autowired
    private SearchSessionRepository sessionRepository;

    private UUID testSessionId;

    @BeforeEach
    void setup() {
        // Create test session with required fields
        SearchSession session = new SearchSession(
                "test-digest-123", // queryDigest
                "test query", // normalizedQuery
                "youtube", // platform
                5, // totalResults
                0, // youtubeQuotaUsed
                Instant.now().plusSeconds(3600) // expiresAt
        );
        session = sessionRepository.save(session);
        testSessionId = session.getSessionId();

        // Create test results with varying scores for filtering
        // Bucket boundaries from BucketMapper:
        // - Audience: small (0-0.33), medium (0.33-0.66), large (0.66-1.01)
        // - Engagement: low (0-0.33), medium (0.33-0.66), high (0.66-1.01)

        // Result 1: Small audience (0.15), High engagement (0.85)
        createResult(1, 0.15, 0.85, 0.50, 0.60);
        // Result 2: Medium audience (0.45), Medium engagement (0.55)
        createResult(2, 0.45, 0.55, 0.50, 0.50);
        // Result 3: Large audience (0.75), Low engagement (0.25)
        createResult(3, 0.75, 0.25, 0.50, 0.70);
        // Result 4: Small audience (0.10), Low engagement (0.20)
        createResult(4, 0.10, 0.20, 0.50, 0.40);
        // Result 5: Large audience (0.80), High engagement (0.90)
        createResult(5, 0.80, 0.90, 0.70, 0.80);
    }

    private void createResult(int rank, double audienceFit, double engagementQuality,
            double activityConsistency, double competitivenessScore) {
        SearchSessionResult result = new SearchSessionResult();
        result.setSessionId(testSessionId);
        result.setRank(rank);
        result.setChannelId("CH" + rank);
        result.setChannelName("Channel " + rank);
        result.setAudienceFit(audienceFit);
        result.setEngagementQuality(engagementQuality);
        result.setActivityConsistency(activityConsistency);
        result.setCompetitivenessScore(competitivenessScore);
        result.setSubscriberCount(rank * 10000L);
        result.setScore(0.5);
        result.setGenreRelevance(0.5);
        resultRepository.save(result);
    }

    @Test
    @DisplayName("OR within single category: small OR large audience")
    void testOrWithinCategory() {
        // Filter: audience = [small, large] (should match 0.00-0.33 OR 0.66-1.01)
        FilterCriteria filters = new FilterCriteria(
                Set.of("small", "large"), // audience
                null, // engagement
                null, // competitiveness
                null, // activity
                null, // platform
                null // genres
        );

        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, filters);
        Page<SearchSessionResult> results = resultRepository.findAll(spec, PageRequest.of(0, 10));

        // Should match: Result 1 (small), Result 4 (small), Result 3 (large), Result 5
        // (large)
        assertThat(results.getContent()).hasSize(4);
        assertThat(results.getContent())
                .extracting(SearchSessionResult::getRank)
                .containsExactlyInAnyOrder(1, 3, 4, 5);
    }

    @Test
    @DisplayName("AND across categories: small audience AND high engagement")
    void testAndAcrossCategories() {
        // Filter: audience = [small] AND engagement = [high]
        FilterCriteria filters = new FilterCriteria(
                Set.of("small"), // audience (0.00-0.33)
                Set.of("high"), // engagement (0.66-1.01)
                null, null, null, null);

        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, filters);
        Page<SearchSessionResult> results = resultRepository.findAll(spec, PageRequest.of(0, 10));

        // Should match: Result 1 only (small audience AND high engagement)
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("Combined OR and AND: (small OR large) AND high engagement")
    void testCombinedOrAndLogic() {
        // Filter: audience = [small, large] AND engagement = [high]
        FilterCriteria filters = new FilterCriteria(
                Set.of("small", "large"),
                Set.of("high"),
                null, null, null, null);

        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, filters);
        Page<SearchSessionResult> results = resultRepository.findAll(spec, PageRequest.of(0, 10));

        // Should match: Result 1 (small + high), Result 5 (large + high)
        assertThat(results.getContent()).hasSize(2);
        assertThat(results.getContent())
                .extracting(SearchSessionResult::getRank)
                .containsExactlyInAnyOrder(1, 5);
    }

    @Test
    @DisplayName("Empty filters returns all results")
    void testEmptyFilters() {
        FilterCriteria filters = FilterCriteria.empty();

        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, filters);
        Page<SearchSessionResult> results = resultRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(5);
    }

    @Test
    @DisplayName("Null filters returns all results")
    void testNullFilters() {
        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, null);
        Page<SearchSessionResult> results = resultRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(5);
    }

    @Test
    @DisplayName("Count query matches filter exactly (Condition 4)")
    void testCountMatchesFilter() {
        FilterCriteria filters = new FilterCriteria(
                Set.of("small"), null, null, null, null, null);

        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, filters);
        Page<SearchSessionResult> page1 = resultRepository.findAll(spec, PageRequest.of(0, 1));
        long count = resultRepository.count(spec);

        // Both should report same total
        assertThat(page1.getTotalElements()).isEqualTo(count);
        assertThat(count).isEqualTo(2); // Results 1 and 4
    }

    @Test
    @DisplayName("Filtering combined with sorting (DB-authoritative)")
    void testFilteringWithSorting() {
        FilterCriteria filters = new FilterCriteria(
                Set.of("small", "large"), null, null, null, null, null);

        var spec = SearchSessionResultSpecification.fromCriteria(testSessionId, filters);
        Sort sort = Sort.by(Sort.Order.desc("subscriberCount"));
        Page<SearchSessionResult> results = resultRepository.findAll(spec, PageRequest.of(0, 10, sort));

        // Should be sorted by subscriber count descending: 5, 3, 1, 4
        assertThat(results.getContent())
                .extracting(SearchSessionResult::getSubscriberCount)
                .containsExactly(50000L, 30000L, 10000L, 10000L);
    }
}
