package com.hermes.controller;

import com.hermes.domain.dto.SearchRequest;
import com.hermes.domain.dto.SearchResult;
import com.hermes.domain.enums.SortKey;
import com.hermes.feature.FeatureRegistry;
import com.hermes.governor.TokenGovernor;
import com.hermes.governor.YouTubeQuotaGovernor;
import com.hermes.service.SearchService;
import com.hermes.service.SearchSessionService;
import com.hermes.service.YouTubeSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // For local development flexibility
public class SearchController {

        private final SearchService searchService;
        private final SearchSessionService sessionService;
        private final YouTubeSearchService youTubeSearchService;
        private final YouTubeQuotaGovernor youtubeQuotaGovernor;
        private final TokenGovernor tokenGovernor;
        private final FeatureRegistry featureRegistry;

        public SearchController(
                        SearchService searchService,
                        SearchSessionService sessionService,
                        YouTubeSearchService youTubeSearchService,
                        YouTubeQuotaGovernor youtubeQuotaGovernor,
                        TokenGovernor tokenGovernor,
                        FeatureRegistry featureRegistry) {
                this.searchService = searchService;
                this.sessionService = sessionService;
                this.youTubeSearchService = youTubeSearchService;
                this.youtubeQuotaGovernor = youtubeQuotaGovernor;
                this.tokenGovernor = tokenGovernor;
                this.featureRegistry = featureRegistry;
        }

        /**
         * Execute search. Returns sessionId for zero-API pagination.
         * First execution hits YouTube API, subsequent calls use cached session.
         */
        @PostMapping("/search")
        public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
                SearchResult searchResult = searchService.performSearch(request);

                var ranked = searchResult.paginatedResults();
                if (ranked == null) {
                        ranked = com.hermes.domain.dto.PaginatedResult.empty(request.page(), 10);
                }

                return ResponseEntity.ok(Map.of(
                                "sessionId",
                                searchResult.sessionId() != null ? searchResult.sessionId().toString() : "",
                                "results", ranked.creators(),
                                "totalResults", ranked.totalResults(),
                                "currentPage", ranked.page(),
                                "totalPages", ranked.totalPages(),
                                "fromCache", searchResult.fromCache(),
                                "youtubeQuotaUsed", searchResult.youtubeQuotaUsed(),
                                "queryInfo", searchResult.queryInfo() != null ? searchResult.queryInfo() : Map.of(),
                                "channelResults",
                                searchResult.channelResults() != null ? searchResult.channelResults() : Map.of()));
        }

        /**
         * Paginate an existing session. ZERO API calls.
         * Use the sessionId from the initial search response.
         * 
         * @param sortBy Optional sort key: FINAL_SCORE (default), RELEVANCE,
         *               SUBSCRIBERS, ENGAGEMENT, ACTIVITY
         */
        @GetMapping("/search/session/{sessionId}")
        public ResponseEntity<Map<String, Object>> paginateSession(
                        @PathVariable UUID sessionId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int pageSize,
                        @RequestParam(defaultValue = "FINAL_SCORE") String sortBy) {

                // Parse and validate sort key (defaults to FINAL_SCORE if invalid)
                SortKey sortKey = SortKey.fromString(sortBy);

                SearchResult result = searchService.paginateSession(sessionId, page, pageSize, sortKey);

                if (result.sessionId() == null) {
                        return ResponseEntity.notFound().build();
                }

                var ranked = result.paginatedResults();
                return ResponseEntity.ok(Map.of(
                                "sessionId", sessionId.toString(),
                                "results", ranked.creators(),
                                "totalResults", ranked.totalResults(),
                                "currentPage", ranked.page(),
                                "totalPages", ranked.totalPages(),
                                "sortKey", sortKey.name(),
                                "fromCache", true,
                                "youtubeQuotaUsed", 0));
        }

        /**
         * Paginate with FILTERING and SORTING. ZERO API calls.
         * 
         * Multi-select filters use comma-separated values:
         * - audience: small,medium,large
         * - engagement: low,medium,high
         * - competitiveness: emerging,growing,established,dominant
         * - activity: occasional,consistent,very_active
         * - genres: comedy,gaming,etc
         */
        @GetMapping("/search/session/{sessionId}/filtered")
        public ResponseEntity<Map<String, Object>> paginateFiltered(
                        @PathVariable UUID sessionId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int pageSize,
                        @RequestParam(defaultValue = "FINAL_SCORE") String sortBy,
                        @RequestParam(required = false) String audience,
                        @RequestParam(required = false) String engagement,
                        @RequestParam(required = false) String competitiveness,
                        @RequestParam(required = false) String activity,
                        @RequestParam(required = false) String genres) {

                SortKey sortKey = SortKey.fromString(sortBy);

                // Parse comma-separated filter values into Sets
                com.hermes.domain.dto.FilterCriteria filters = new com.hermes.domain.dto.FilterCriteria(
                                parseCommaSeparated(audience),
                                parseCommaSeparated(engagement),
                                parseCommaSeparated(competitiveness),
                                parseCommaSeparated(activity),
                                null, // platform not used yet
                                parseCommaSeparated(genres));

                var result = sessionService.paginateFiltered(sessionId, page, pageSize, sortKey, filters);

                if (result.sessionId() == null) {
                        return ResponseEntity.notFound().build();
                }

                // Convert to GradedCreator for response
                var creators = result.results().stream()
                                .map(this::toGradedCreator)
                                .toList();

                return ResponseEntity.ok(Map.of(
                                "sessionId", sessionId.toString(),
                                "results", creators,
                                "totalResults", result.totalFiltered(),
                                "currentPage", result.currentPage(),
                                "totalPages", result.totalPages(),
                                "sortKey", sortKey.name(),
                                "activeFilters", result.activeFilters(),
                                "fromCache", true,
                                "youtubeQuotaUsed", 0));
        }

        private java.util.Set<String> parseCommaSeparated(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                return java.util.Arrays.stream(value.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toSet());
        }

        private com.hermes.domain.grading.GradedCreator toGradedCreator(
                        com.hermes.domain.entity.SearchSessionResult r) {
                com.hermes.domain.grading.CreatorScore score = com.hermes.domain.grading.CreatorScore.compute(
                                r.getGenreRelevance(),
                                r.getAudienceFit(),
                                r.getEngagementQuality(),
                                r.getActivityConsistency(),
                                0.5 // freshness default
                );
                return new com.hermes.domain.grading.GradedCreator(
                                r.getChannelId(),
                                r.getChannelName(),
                                r.getDescription(),
                                r.getProfileImageUrl(),
                                "youtube",
                                score,
                                r.getLabels() != null ? java.util.Arrays.asList(r.getLabels()) : java.util.List.of(),
                                r.getSubscriberCount(),
                                0L,
                                r.getLastVideoDate());
        }

        /**
         * Admin stats endpoint for monitoring.
         * Returns session, quota, and cache statistics.
         */
        @GetMapping("/admin/stats")
        public ResponseEntity<Map<String, Object>> getStats() {
                // Session stats
                var sessionStats = sessionService.getStats();

                // YouTube quota stats
                var youtubeQuotaStats = youtubeQuotaGovernor.getStats();

                // LLM token stats
                var llmTokenStats = tokenGovernor.getStats();

                // Channel cache stats
                var channelCacheStats = youTubeSearchService.getCacheStats();

                return ResponseEntity.ok(Map.of(
                                "sessions", Map.of(
                                                "activeSessions", sessionStats.activeSessions(),
                                                "l1CacheHits", sessionStats.l1CacheHits(),
                                                "l1CacheMisses", sessionStats.l1CacheMisses(),
                                                "l1HitRate", String.format("%.2f%%", sessionStats.l1HitRate() * 100)),
                                "youtubeQuota", Map.of(
                                                "unitsUsed", youtubeQuotaStats.unitsUsed(),
                                                "dailyLimit", youtubeQuotaStats.dailyLimit(),
                                                "remainingUnits", youtubeQuotaStats.remainingUnits(),
                                                "usagePercent",
                                                String.format("%.2f%%", youtubeQuotaStats.usageRatio() * 100),
                                                "date", youtubeQuotaStats.date().toString()),
                                "llmTokens", Map.of(
                                                "tokensUsed", llmTokenStats.tokensUsed(),
                                                "dailyBudget", llmTokenStats.dailyBudget(),
                                                "remainingBudget", llmTokenStats.remainingBudget(),
                                                "usagePercent",
                                                String.format("%.2f%%", llmTokenStats.usageRatio() * 100)),
                                "channelCache", Map.of(
                                                "cacheSize", channelCacheStats.cacheSize(),
                                                "hits", channelCacheStats.hits(),
                                                "misses", channelCacheStats.misses(),
                                                "hitRate",
                                                String.format("%.2f%%", channelCacheStats.hitRate() * 100))));
        }

        /**
         * Feature flags status endpoint.
         * Returns all feature states for observability.
         */
        @GetMapping("/admin/features")
        public ResponseEntity<Map<String, Object>> getFeatures() {
                var summary = featureRegistry.getStatusSummary();

                // Build feature details map
                Map<String, Object> features = new LinkedHashMap<>();
                summary.features().forEach((flag, state) -> {
                        features.put(flag.name(), Map.of(
                                        "state", state.name(),
                                        "active", state.isActive(),
                                        "hasCredentials", state.hasCredentials()));
                });

                return ResponseEntity.ok(Map.of(
                                "summary", Map.of(
                                                "enabled", summary.enabledCount(),
                                                "configured", summary.configuredCount(),
                                                "disabled", summary.disabledCount()),
                                "features", features,
                                "enabledFeatures", featureRegistry.getEnabledFeatures()));
        }

        /**
         * Clears all caches (channel cache + expired sessions).
         * Useful for forcing fresh data fetch with updated thumbnail quality.
         */
        @PostMapping("/admin/cache/clear")
        public ResponseEntity<Map<String, Object>> clearCache() {
                long channelsCleaned = youTubeSearchService.clearCache();
                sessionService.cleanupExpiredSessions();

                return ResponseEntity.ok(Map.of(
                                "channelsCleaned", channelsCleaned,
                                "message", "Cache cleared. New searches will fetch fresh high-res thumbnails."));
        }

        /**
         * Health check endpoint.
         */
        @GetMapping("/health")
        public ResponseEntity<Map<String, String>> health() {
                return ResponseEntity.ok(Map.of("status", "healthy"));
        }
}
