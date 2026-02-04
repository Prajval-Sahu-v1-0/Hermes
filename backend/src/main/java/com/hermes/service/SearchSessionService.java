package com.hermes.service;

import com.hermes.cache.QueryNormalizer;
import com.hermes.domain.entity.SearchSession;
import com.hermes.domain.entity.SearchSessionResult;
import com.hermes.domain.enums.SortKey;
import com.hermes.domain.grading.GradedCreator;
import com.hermes.grading.scorer.CompetitivenessScorer;
import com.hermes.repository.SearchSessionRepository;
import com.hermes.repository.SearchSessionResultRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages search session lifecycle.
 * - Creates materialized sessions from search results
 * - Provides zero-API-call pagination
 * - Handles session expiry and cleanup
 */
@Service
public class SearchSessionService {

    private static final Logger log = LoggerFactory.getLogger(SearchSessionService.class);

    private final SearchSessionRepository sessionRepository;
    private final SearchSessionResultRepository resultRepository;
    private final QueryNormalizer queryNormalizer;

    // L1 cache for session existence checks (avoid DB round-trip)
    private final Cache<String, UUID> sessionIdCache;

    @Value("${hermes.session.ttl-minutes:30}")
    private int sessionTtlMinutes;

    @Value("${hermes.session.sliding-expiration:true}")
    private boolean slidingExpiration;

    public SearchSessionService(
            SearchSessionRepository sessionRepository,
            SearchSessionResultRepository resultRepository,
            QueryNormalizer queryNormalizer) {
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.queryNormalizer = queryNormalizer;

        // L1 cache: 1000 sessions, 5 min TTL
        this.sessionIdCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    /**
     * Find an existing valid session by query.
     * Checks L1 cache first, then database.
     */
    @Transactional
    public Optional<SearchSession> findValidSession(String genre, String platform) {
        String digest = queryNormalizer.getCacheKey(genre);

        // L1 cache check
        UUID cachedId = sessionIdCache.getIfPresent(digest + ":" + platform);
        if (cachedId != null) {
            Optional<SearchSession> session = sessionRepository.findById(cachedId);
            if (session.isPresent() && !session.get().isExpired()) {
                log.debug("[Session] L1 cache hit for digest: {}", digest.substring(0, 8));
                if (slidingExpiration) {
                    touchSession(cachedId);
                }
                return session;
            }
            // Stale cache entry
            sessionIdCache.invalidate(digest + ":" + platform);
        }

        // L2 (database) lookup
        Optional<SearchSession> session = sessionRepository.findValidSession(
                digest, platform, Instant.now());

        if (session.isPresent()) {
            log.info("[Session] L2 cache hit for query: {} (sessionId: {})",
                    genre, session.get().getSessionId());
            // Populate L1 cache
            sessionIdCache.put(digest + ":" + platform, session.get().getSessionId());
            if (slidingExpiration) {
                touchSession(session.get().getSessionId());
            }
        }

        return session;
    }

    /**
     * Create a new session or update an existing one (upsert).
     * Materializes ranked results into the database.
     */
    @Transactional
    public SearchSession createSession(String genre, String platform,
            List<GradedCreator> rankedResults,
            int youtubeQuotaUsed) {
        String normalized = queryNormalizer.normalize(genre);
        String digest = queryNormalizer.getCacheKey(genre);

        // Check for existing session (valid or expired) to avoid Unique Constraint
        // violation
        Optional<SearchSession> existingOpt = sessionRepository.findByQueryDigestAndPlatform(digest, platform);
        SearchSession session;

        if (existingOpt.isPresent()) {
            // Update existing session
            session = existingOpt.get();
            log.info("[Session] Updating existing session {} (was found for upsert)", session.getSessionId());

            session.setTotalResults(rankedResults.size());
            session.setYoutubeQuotaUsed(session.getYoutubeQuotaUsed() + youtubeQuotaUsed); // Accumulate quota? Or
                                                                                           // reset? Let's accumulate
                                                                                           // for history.
            session.setExpiresAt(Instant.now().plus(Duration.ofMinutes(sessionTtlMinutes)));
            session.setLastAccessedAt(Instant.now());

            // Clear old results before re-materializing
            resultRepository.deleteBySessionId(session.getSessionId());
        } else {
            // Create new session
            session = new SearchSession(
                    digest,
                    normalized,
                    platform,
                    rankedResults.size(),
                    youtubeQuotaUsed,
                    Instant.now().plus(Duration.ofMinutes(sessionTtlMinutes)));
        }

        session = sessionRepository.save(session);
        log.info("[Session] Persisted session {} with {} results",
                session.getSessionId(), rankedResults.size());

        // Materialize ranked results
        materializeResults(session.getSessionId(), rankedResults);

        // Populate L1 cache
        sessionIdCache.put(digest + ":" + platform, session.getSessionId());

        return session;
    }

    /**
     * Store ranked results in database for zero-API pagination.
     */
    private void materializeResults(UUID sessionId, List<GradedCreator> rankedResults) {
        List<SearchSessionResult> results = new ArrayList<>(rankedResults.size());

        for (int i = 0; i < rankedResults.size(); i++) {
            GradedCreator creator = rankedResults.get(i);
            SearchSessionResult result = new SearchSessionResult(
                    sessionId,
                    i + 1, // 1-indexed rank
                    creator.channelId(),
                    creator.channelName(),
                    creator.description(),
                    creator.profileImageUrl(),
                    creator.score().finalScore(),
                    creator.labels().toArray(new String[0]));
            result.setGenreRelevance(creator.score().genreRelevance());
            result.setAudienceFit(creator.score().audienceFit());
            result.setEngagementQuality(creator.score().engagementQuality());
            result.setActivityConsistency(creator.score().activityConsistency());

            // Compute competitiveness score ONCE during materialization
            // Formula: 0.40*audience + 0.35*engagement + 0.25*activity (as growth proxy)
            double competitiveness = CompetitivenessScorer.computeFromScore(creator.score());
            result.setCompetitivenessScore(competitiveness);

            // Store raw subscriber count for "Most Subscribers" sorting
            result.setSubscriberCount(creator.subscriberCount());

            // Store last video date for "Recently Active" sorting
            result.setLastVideoDate(creator.lastVideoDate());

            results.add(result);
        }

        resultRepository.saveAll(results);
        log.debug("[Session] Materialized {} results for session {}", results.size(), sessionId);
    }

    /**
     * Paginate stored results. ZERO API calls.
     */
    @Transactional
    public SessionPage paginate(UUID sessionId, int page, int pageSize) {
        // Validate session exists
        Optional<SearchSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("[Session] Session not found: {}", sessionId);
            return SessionPage.empty();
        }

        SearchSession session = sessionOpt.get();
        if (session.isExpired()) {
            log.warn("[Session] Session expired: {}", sessionId);
            return SessionPage.expired(sessionId);
        }

        // Touch for sliding expiration
        if (slidingExpiration) {
            touchSession(sessionId);
        }

        // Paginate from database
        List<SearchSessionResult> results = resultRepository.findBySessionIdPaginated(
                sessionId, PageRequest.of(page, pageSize));

        int totalPages = (int) Math.ceil((double) session.getTotalResults() / pageSize);

        log.debug("[Session] Paginated session {} page {} ({} results)",
                sessionId, page, results.size());

        return new SessionPage(
                sessionId,
                session.getNormalizedQuery(),
                results,
                session.getTotalResults(),
                totalPages,
                page,
                pageSize,
                true, // fromCache
                0 // Zero API calls for pagination
        );
    }

    /**
     * Cursor-based pagination (more efficient for deep pagination).
     */
    @Transactional
    public SessionPage paginateAfterRank(UUID sessionId, int lastRank, int pageSize) {
        Optional<SearchSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || sessionOpt.get().isExpired()) {
            return SessionPage.empty();
        }

        SearchSession session = sessionOpt.get();
        if (slidingExpiration) {
            touchSession(sessionId);
        }

        List<SearchSessionResult> results = resultRepository.findAfterRank(
                sessionId, lastRank, PageRequest.of(0, pageSize));

        int totalPages = (int) Math.ceil((double) session.getTotalResults() / pageSize);

        return new SessionPage(
                sessionId,
                session.getNormalizedQuery(),
                results,
                session.getTotalResults(),
                totalPages,
                -1, // Not applicable for cursor
                pageSize,
                true,
                0);
    }

    /**
     * Paginate stored results with SORTING by specified key.
     * 
     * ZERO API calls. Pure read-time view over fixed result set.
     * Sorting operates on precomputed, stored values ONLY.
     * 
     * @param sessionId Session to paginate
     * @param page      Page number (0-indexed)
     * @param pageSize  Results per page
     * @param sortKey   Sort key (whitelisted only)
     * @return Sorted, paginated results
     */
    @Transactional(readOnly = true)
    public SessionPage paginateSorted(UUID sessionId, int page, int pageSize, SortKey sortKey) {
        // Validate session exists
        Optional<SearchSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("[Session] Session not found: {}", sessionId);
            return SessionPage.empty();
        }

        SearchSession session = sessionOpt.get();
        if (session.isExpired()) {
            log.warn("[Session] Session expired: {}", sessionId);
            return SessionPage.expired(sessionId);
        }

        // Touch for sliding expiration
        if (slidingExpiration) {
            touchSession(sessionId);
        }

        // Get sorted results using switch dispatch (type-safe, no dynamic SQL)
        List<SearchSessionResult> results = switch (sortKey) {
            case FINAL_SCORE -> resultRepository.findBySessionIdOrderByScoreDesc(
                    sessionId, PageRequest.of(page, pageSize));
            case RELEVANCE -> resultRepository.findBySessionIdOrderByRelevanceDesc(
                    sessionId, PageRequest.of(page, pageSize));
            case SUBSCRIBERS -> resultRepository.findBySessionIdOrderBySubscribersDesc(
                    sessionId, PageRequest.of(page, pageSize));
            case ENGAGEMENT -> resultRepository.findBySessionIdOrderByEngagementDesc(
                    sessionId, PageRequest.of(page, pageSize));
            case ACTIVITY -> resultRepository.findBySessionIdOrderByActivityDesc(
                    sessionId, PageRequest.of(page, pageSize));
            case COMPETITIVENESS -> resultRepository.findBySessionIdOrderByCompetitivenessDesc(
                    sessionId, PageRequest.of(page, pageSize));
        };

        int totalPages = (int) Math.ceil((double) session.getTotalResults() / pageSize);

        log.debug("[Session] Sorted pagination session={} sortKey={} page={} results={}",
                sessionId, sortKey, page, results.size());

        return new SessionPage(
                sessionId,
                session.getNormalizedQuery(),
                results,
                session.getTotalResults(),
                totalPages,
                page,
                pageSize,
                true, // fromCache
                0 // ZERO API calls - sorting is pure read
        );
    }

    /**
     * Paginate stored results with FILTERING and SORTING.
     * 
     * EXECUTION ORDER (MANDATORY):
     * 1. Resolve SearchSession
     * 2. Apply multi-select filters (OR within, AND across)
     * 3. Apply sorting
     * 4. Apply pagination
     * 
     * INVARIANTS:
     * - ZERO API calls
     * - NO recomputation
     * - Session-scoped, read-only
     * 
     * @param sessionId Session to paginate
     * @param page      Page number (0-indexed)
     * @param pageSize  Results per page
     * @param sortKey   Sort key
     * @param filters   Multi-select filter criteria
     * @return Filtered, sorted, paginated results
     */
    @Transactional(readOnly = true)
    public FilteredSessionPage paginateFiltered(
            UUID sessionId,
            int page,
            int pageSize,
            SortKey sortKey,
            com.hermes.domain.dto.FilterCriteria filters) {

        // 1. Resolve session
        Optional<SearchSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("[Session] Session not found: {}", sessionId);
            return FilteredSessionPage.empty();
        }

        SearchSession session = sessionOpt.get();
        if (session.isExpired()) {
            log.warn("[Session] Session expired: {}", sessionId);
            return FilteredSessionPage.expired(sessionId);
        }

        if (slidingExpiration) {
            touchSession(sessionId);
        }

        // 2. Fetch all results for this session (for filtering)
        List<SearchSessionResult> allResults = resultRepository.findBySessionIdOrderByRank(sessionId);

        // 3. Apply filters (if any)
        List<SearchSessionResult> filteredResults = allResults;
        if (filters != null && !filters.isEmpty()) {
            filteredResults = applyFiltersInMemory(allResults, filters);
        }

        // 4. Apply sorting
        filteredResults = sortResults(filteredResults, sortKey);

        // 5. Apply pagination
        int totalFiltered = filteredResults.size();
        int totalPages = (int) Math.ceil((double) totalFiltered / pageSize);
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalFiltered);

        List<SearchSessionResult> pagedResults = startIndex < totalFiltered
                ? filteredResults.subList(startIndex, endIndex)
                : List.of();

        log.debug("[Session] Filtered pagination session={} sortKey={} page={} total={} filtered={}",
                sessionId, sortKey, page, allResults.size(), totalFiltered);

        return new FilteredSessionPage(
                sessionId,
                session.getNormalizedQuery(),
                pagedResults,
                totalFiltered,
                totalPages,
                page,
                pageSize,
                sortKey,
                filters != null ? filters.activeFilterCount() : 0,
                true,
                0);
    }

    /**
     * Applies filters in memory using OR within category, AND across categories.
     */
    private List<SearchSessionResult> applyFiltersInMemory(
            List<SearchSessionResult> results,
            com.hermes.domain.dto.FilterCriteria filters) {

        return results.stream()
                .filter(r -> passesAllFilters(r, filters))
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean passesAllFilters(SearchSessionResult r, com.hermes.domain.dto.FilterCriteria f) {
        // Audience
        if (f.audience() != null && !f.audience().isEmpty()) {
            if (!com.hermes.service.filter.BucketMapper.matchesAnyAudienceBucket(r.getAudienceFit(), f.audience())) {
                return false;
            }
        }
        // Engagement
        if (f.engagement() != null && !f.engagement().isEmpty()) {
            if (!com.hermes.service.filter.BucketMapper.matchesAnyEngagementBucket(r.getEngagementQuality(),
                    f.engagement())) {
                return false;
            }
        }
        // Competitiveness
        if (f.competitiveness() != null && !f.competitiveness().isEmpty()) {
            if (!com.hermes.service.filter.BucketMapper.matchesAnyCompetitivenessBucket(r.getCompetitivenessScore(),
                    f.competitiveness())) {
                return false;
            }
        }
        // Activity
        if (f.activity() != null && !f.activity().isEmpty()) {
            if (!com.hermes.service.filter.BucketMapper.matchesAnyActivityBucket(r.getActivityConsistency(),
                    f.activity())) {
                return false;
            }
        }
        // Genres (check labels)
        if (f.genres() != null && !f.genres().isEmpty()) {
            String[] labels = r.getLabels();
            if (labels == null || labels.length == 0)
                return false;
            java.util.Set<String> lowerGenres = f.genres().stream()
                    .map(String::toLowerCase)
                    .collect(java.util.stream.Collectors.toSet());
            boolean hasMatch = java.util.Arrays.stream(labels)
                    .map(String::toLowerCase)
                    .anyMatch(lowerGenres::contains);
            if (!hasMatch)
                return false;
        }
        return true;
    }

    /**
     * Sorts results in memory by the specified sort key.
     */
    private List<SearchSessionResult> sortResults(List<SearchSessionResult> results, SortKey sortKey) {
        java.util.Comparator<SearchSessionResult> comparator = switch (sortKey) {
            case FINAL_SCORE -> java.util.Comparator.comparingDouble(SearchSessionResult::getScore).reversed();
            case RELEVANCE -> java.util.Comparator.comparingDouble(SearchSessionResult::getGenreRelevance).reversed();
            case SUBSCRIBERS -> java.util.Comparator.comparingLong(SearchSessionResult::getSubscriberCount).reversed();
            case ENGAGEMENT ->
                java.util.Comparator.comparingDouble(SearchSessionResult::getEngagementQuality).reversed();
            case ACTIVITY ->
                java.util.Comparator.comparing(
                        SearchSessionResult::getLastVideoDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
            case COMPETITIVENESS ->
                java.util.Comparator.comparingDouble(SearchSessionResult::getCompetitivenessScore).reversed();
        };
        return results.stream().sorted(comparator).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Filtered session page DTO with filter metadata.
     */
    public record FilteredSessionPage(
            UUID sessionId,
            String query,
            List<SearchSessionResult> results,
            int totalFiltered,
            int totalPages,
            int currentPage,
            int pageSize,
            SortKey sortKey,
            int activeFilters,
            boolean fromCache,
            int apiCallsMade) {
        public static FilteredSessionPage empty() {
            return new FilteredSessionPage(null, null, List.of(), 0, 0, 0, 0, SortKey.FINAL_SCORE, 0, false, 0);
        }

        public static FilteredSessionPage expired(UUID sessionId) {
            return new FilteredSessionPage(sessionId, null, List.of(), 0, 0, 0, 0, SortKey.FINAL_SCORE, 0, false, 0);
        }
    }

    /**
     * Update session access time for sliding expiration.
     */
    @Transactional
    public void touchSession(UUID sessionId) {
        Instant newExpiry = Instant.now().plus(Duration.ofMinutes(sessionTtlMinutes));
        sessionRepository.extendExpiry(sessionId, newExpiry, Instant.now());
    }

    /**
     * Cleanup expired sessions (runs every 5 minutes).
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupExpiredSessions() {
        int deleted = sessionRepository.deleteExpiredSessions(Instant.now());
        if (deleted > 0) {
            log.info("[Session] Cleaned up {} expired sessions", deleted);
        }
    }

    /**
     * Get session statistics for monitoring.
     */
    public SessionStats getStats() {
        return new SessionStats(
                sessionRepository.countActiveSessions(Instant.now()),
                sessionIdCache.stats().hitCount(),
                sessionIdCache.stats().missCount(),
                sessionIdCache.stats().hitRate());
    }

    // DTOs
    public record SessionPage(
            UUID sessionId,
            String query,
            List<SearchSessionResult> results,
            int totalResults,
            int totalPages,
            int currentPage,
            int pageSize,
            boolean fromCache,
            int apiCallsMade) {
        public static SessionPage empty() {
            return new SessionPage(null, null, List.of(), 0, 0, 0, 0, false, 0);
        }

        public static SessionPage expired(UUID sessionId) {
            return new SessionPage(sessionId, null, List.of(), 0, 0, 0, 0, false, 0);
        }
    }

    public record SessionStats(
            long activeSessions,
            long l1CacheHits,
            long l1CacheMisses,
            double l1HitRate) {
    }
}
