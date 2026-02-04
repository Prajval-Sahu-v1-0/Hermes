package com.hermes.service;

import com.hermes.domain.dto.PaginatedResult;
import com.hermes.domain.dto.QueryResultsMap;
import com.hermes.domain.dto.SearchRequest;
import com.hermes.domain.dto.SearchResult;
import com.hermes.domain.entity.SearchSession;
import com.hermes.domain.entity.SearchSessionResult;
import com.hermes.domain.enums.SortKey;
import com.hermes.domain.grading.CreatorScore;
import com.hermes.domain.grading.GradedCreator;
import com.hermes.ingestion.CreatorIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

        private static final Logger log = LoggerFactory.getLogger(SearchService.class);

        private final QueryGenerationService queryGenerationService;
        private final YouTubeSearchService youTubeSearchService;
        private final CreatorPersistenceService creatorPersistenceService;
        private final CreatorIngestionService creatorIngestionService;
        private final RankingService rankingService;
        private final SearchSessionService sessionService;

        public SearchService(
                        QueryGenerationService queryGenerationService,
                        YouTubeSearchService youTubeSearchService,
                        CreatorPersistenceService creatorPersistenceService,
                        CreatorIngestionService creatorIngestionService,
                        RankingService rankingService,
                        SearchSessionService sessionService) {
                this.queryGenerationService = queryGenerationService;
                this.youTubeSearchService = youTubeSearchService;
                this.creatorPersistenceService = creatorPersistenceService;
                this.creatorIngestionService = creatorIngestionService;
                this.rankingService = rankingService;
                this.sessionService = sessionService;
        }

        /**
         * Main search entry point.
         * Uses session-based caching: execute once, paginate infinitely.
         */
        public SearchResult performSearch(SearchRequest request) {
                String platform = request.platform() != null ? request.platform() : "youtube";
                int page = request.page();
                int pageSize = request.pageSize() > 0 ? request.pageSize() : 10;

                // PHASE 1: Check for existing valid session (CACHE HIT = 0 API calls)
                Optional<SearchSession> existingSession = sessionService.findValidSession(
                                request.genre(), platform);

                if (existingSession.isPresent()) {
                        log.info("[Search] SESSION HIT for '{}' - 0 API calls", request.genre());
                        return paginateFromSession(existingSession.get(), page, pageSize);
                }

                // PHASE 2: Cache miss - execute full search ONCE
                log.info("[Search] SESSION MISS for '{}' - executing full search", request.genre());
                return executeAndMaterialize(request, platform, page, pageSize);
        }

        /**
         * Paginate from existing session. ZERO API calls.
         */
        private SearchResult paginateFromSession(SearchSession session, int page, int pageSize) {
                SearchSessionService.SessionPage sessionPage = sessionService.paginate(
                                session.getSessionId(), page, pageSize);

                // Convert stored results to GradedCreator for backward compatibility
                List<GradedCreator> creators = sessionPage.results().stream()
                                .map(this::toGradedCreator)
                                .collect(Collectors.toList());

                PaginatedResult paginatedResult = new PaginatedResult(
                                creators,
                                page,
                                pageSize,
                                sessionPage.totalResults(),
                                sessionPage.totalPages());

                return SearchResult.fromSession(
                                session.getSessionId(),
                                session.getNormalizedQuery(),
                                paginatedResult,
                                true, // fromCache
                                0 // Zero API calls
                );
        }

        /**
         * Execute full search and materialize results for future pagination.
         */
        private SearchResult executeAndMaterialize(SearchRequest request, String platform,
                        int page, int pageSize) {
                // Phase One: Generate intent-based queries using Cohere AI (cached)
                var queryResult = queryGenerationService.generateQueries(request.genre());

                // Phase Two: Search YouTube for channels (quota consumed HERE)
                QueryResultsMap channelResults = youTubeSearchService.searchChannels(
                                queryResult.queries(),
                                50);

                // Estimate YouTube quota used
                int quotaUsed = estimateYouTubeQuota(queryResult.queries().size(), channelResults);

                // Phase 2.5: Persist discovered creators
                creatorPersistenceService.persistDiscoveredCreators(
                                channelResults.queryResults(),
                                request.genre());

                // Phase 2.6: Trigger async embedding ingestion
                triggerAsyncIngestion(channelResults.queryResults(), request.genre());

                // Phase Three: Grade creators from fresh results
                Map<String, List<GradedCreator>> gradedByQuery = gradeCreatorsFromFreshResults(
                                channelResults.queryResults(),
                                request);

                // Phase Four: Merge, deduplicate, and get FULL ranked list
                List<GradedCreator> allRankedCreators = rankingService.mergeAndRank(gradedByQuery);

                // Phase Five: MATERIALIZE - Store session for future pagination
                SearchSession session = sessionService.createSession(
                                request.genre(),
                                platform,
                                allRankedCreators,
                                quotaUsed);

                log.info("[Search] Materialized {} results into session {} (quota: {})",
                                allRankedCreators.size(), session.getSessionId(), quotaUsed);

                // Phase Six: Return first page
                int totalPages = (int) Math.ceil((double) allRankedCreators.size() / pageSize);
                int fromIndex = page * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, allRankedCreators.size());

                List<GradedCreator> pageResults = fromIndex < allRankedCreators.size()
                                ? allRankedCreators.subList(fromIndex, toIndex)
                                : List.of();

                PaginatedResult paginatedResult = new PaginatedResult(
                                pageResults,
                                page,
                                pageSize,
                                allRankedCreators.size(),
                                totalPages);

                return SearchResult.fromSession(
                                session.getSessionId(),
                                session.getNormalizedQuery(),
                                paginatedResult,
                                false, // Not from cache (first execution)
                                quotaUsed);
        }

        /**
         * Paginate by session ID. For direct pagination API calls.
         */
        public SearchResult paginateSession(UUID sessionId, int page, int pageSize) {
                return paginateSession(sessionId, page, pageSize, SortKey.FINAL_SCORE);
        }

        /**
         * Paginate by session ID with SORTING.
         * 
         * ZERO API calls. Pure read-time view over fixed result set.
         * Sorting operates on precomputed, stored values ONLY.
         */
        public SearchResult paginateSession(UUID sessionId, int page, int pageSize, SortKey sortKey) {
                SearchSessionService.SessionPage sessionPage = sessionService.paginateSorted(
                                sessionId, page, pageSize, sortKey);

                if (sessionPage.sessionId() == null) {
                        log.warn("[Search] Session not found: {}", sessionId);
                        return SearchResult.empty();
                }

                List<GradedCreator> creators = sessionPage.results().stream()
                                .map(this::toGradedCreator)
                                .collect(Collectors.toList());

                PaginatedResult paginatedResult = new PaginatedResult(
                                creators,
                                page,
                                pageSize,
                                sessionPage.totalResults(),
                                sessionPage.totalPages());

                return SearchResult.fromSession(
                                sessionId,
                                sessionPage.query(),
                                paginatedResult,
                                true,
                                0);
        }

        private GradedCreator toGradedCreator(SearchSessionResult result) {
                CreatorScore score = CreatorScore.compute(
                                result.getGenreRelevance(),
                                result.getAudienceFit(),
                                result.getEngagementQuality(),
                                0.7, // Default activity
                                1.0 // Default freshness
                );
                return new GradedCreator(
                                result.getChannelId(),
                                result.getChannelName(),
                                result.getDescription(),
                                result.getProfileImageUrl(),
                                "youtube",
                                score,
                                result.getLabels() != null ? List.of(result.getLabels()) : List.of(),
                                result.getSubscriberCount(),
                                0L,
                                result.getLastVideoDate());
        }

        private int estimateYouTubeQuota(int queryCount, QueryResultsMap results) {
                // search.list costs 100 units per call
                // channels.list costs 1 unit per channel (batched)
                int searchCost = queryCount * 100;
                int channelCount = results.queryResults().values().stream()
                                .mapToInt(List::size).sum();
                int channelCost = (int) Math.ceil(channelCount / 50.0);
                return searchCost + channelCost;
        }

        /**
         * Grades creators directly from fresh YouTube search results.
         * This ensures official channels appear immediately without DB round-trip.
         */
        private Map<String, List<GradedCreator>> gradeCreatorsFromFreshResults(
                        Map<String, List<com.hermes.domain.model.CreatorProfile>> queryResults,
                        SearchRequest request) {

                Map<String, List<GradedCreator>> gradedByQuery = new LinkedHashMap<>();
                String searchTerm = request.genre().toLowerCase().trim();

                for (Map.Entry<String, List<com.hermes.domain.model.CreatorProfile>> entry : queryResults.entrySet()) {
                        String query = entry.getKey();
                        List<com.hermes.domain.model.CreatorProfile> profiles = entry.getValue();

                        if (profiles == null || profiles.isEmpty()) {
                                continue;
                        }

                        List<GradedCreator> graded = new ArrayList<>();
                        for (com.hermes.domain.model.CreatorProfile profile : profiles) {
                                GradedCreator gradedCreator = gradeProfileDirectly(profile, searchTerm);
                                graded.add(gradedCreator);
                        }

                        if (!graded.isEmpty()) {
                                gradedByQuery.put(query, graded);
                        }
                }

                log.info("[Search] Graded {} queries with fresh results for: {}", gradedByQuery.size(), searchTerm);
                return gradedByQuery;
        }

        /**
         * Triggers async embedding generation for newly discovered creators.
         * This is a one-time operation per creator - embeddings are stored permanently.
         */
        private void triggerAsyncIngestion(
                        Map<String, List<com.hermes.domain.model.CreatorProfile>> queryResults,
                        String baseGenre) {
                // Collect all unique profiles
                List<com.hermes.domain.model.CreatorProfile> allProfiles = queryResults.values().stream()
                                .flatMap(List::stream)
                                .distinct()
                                .limit(50) // Limit batch size
                                .toList();

                if (!allProfiles.isEmpty()) {
                        // Async ingestion - won't block search response
                        creatorIngestionService.ingestBatch(allProfiles, baseGenre, baseGenre);
                        log.debug("[Search] Triggered async ingestion for {} creators", allProfiles.size());
                }
        }

        /**
         * Creates a GradedCreator directly from a CreatorProfile.
         * Uses simplified scoring based on available metrics and name matching.
         */
        private GradedCreator gradeProfileDirectly(com.hermes.domain.model.CreatorProfile profile, String searchTerm) {
                // Calculate relevance based on name match (prioritize exact/close matches)
                String channelName = profile.displayName() != null ? profile.displayName().toLowerCase() : "";
                double nameMatchScore = calculateNameMatchScore(channelName, searchTerm);

                // Calculate audience score based on subscriber count
                double audienceScore = calculateAudienceScore(profile.subscriberCount());

                // Calculate engagement score (views per subscriber as a proxy)
                double engagementScore = calculateEngagementScore(profile.viewCount(), profile.subscriberCount());

                // Activity based on video count
                double activityScore = calculateActivityScore(profile.videoCount());

                // Freshness - assume fresh since just discovered
                double freshnessScore = 1.0;

                // Compute final score with boosted name matching weight for person searches
                com.hermes.domain.grading.CreatorScore score = com.hermes.domain.grading.CreatorScore.compute(
                                nameMatchScore, // genreRelevance (repurposed as name relevance)
                                audienceScore,
                                engagementScore,
                                activityScore,
                                freshnessScore);

                // Generate labels
                List<String> labels = new ArrayList<>();
                if (nameMatchScore >= 0.9) {
                        labels.add("Best Match");
                } else if (nameMatchScore >= 0.5) {
                        labels.add("Strong Fit");
                }
                if (profile.subscriberCount() > 1000000) {
                        labels.add("High Influence");
                }
                if (engagementScore > 0.7) {
                        labels.add("Highly engaging content");
                }
                if (labels.isEmpty()) {
                        labels.add("Strong genre alignment");
                }

                return new GradedCreator(
                                profile.id(),
                                profile.displayName(),
                                profile.bio(),
                                profile.profileImageUrl(),
                                "youtube",
                                score,
                                labels,
                                profile.subscriberCount(),
                                profile.viewCount(),
                                profile.lastVideoDate());
        }

        /**
         * Calculates how well the channel name matches the search term.
         * Prioritizes exact matches and close variants.
         */
        private double calculateNameMatchScore(String channelName, String searchTerm) {
                if (channelName.isEmpty() || searchTerm.isEmpty()) {
                        return 0.3;
                }

                // Clean up for comparison
                String cleanChannel = channelName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                String cleanSearch = searchTerm.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

                // Exact match
                if (cleanChannel.equals(cleanSearch)) {
                        return 1.0;
                }

                // Channel name starts with search term (e.g., "PewDiePie" channel)
                if (cleanChannel.startsWith(cleanSearch)) {
                        return 0.95;
                }

                // Search term is contained in channel name
                if (cleanChannel.contains(cleanSearch)) {
                        return 0.8;
                }

                // Channel name contains search term words
                if (channelName.toLowerCase().contains(searchTerm)) {
                        return 0.7;
                }

                // Partial word match
                String[] searchWords = searchTerm.split("\\s+");
                int matchCount = 0;
                for (String word : searchWords) {
                        if (channelName.toLowerCase().contains(word.toLowerCase())) {
                                matchCount++;
                        }
                }
                if (matchCount > 0) {
                        return 0.4 + (0.3 * matchCount / searchWords.length);
                }

                return 0.3; // Base score for any result
        }

        private double calculateAudienceScore(long subscriberCount) {
                if (subscriberCount >= 10_000_000)
                        return 1.0;
                if (subscriberCount >= 1_000_000)
                        return 0.9;
                if (subscriberCount >= 100_000)
                        return 0.7;
                if (subscriberCount >= 10_000)
                        return 0.5;
                if (subscriberCount >= 1_000)
                        return 0.3;
                return 0.2;
        }

        private double calculateEngagementScore(long viewCount, long subscriberCount) {
                if (subscriberCount == 0)
                        return 0.5;

                // Calculate views per subscriber as engagement proxy
                double viewsPerSub = (double) viewCount / subscriberCount;

                // Use sigmoid normalization for continuous scoring (0.0 to 1.0)
                // Midpoint at 50 views/sub, steepness of 0.05
                // This ensures creators are properly ordered by their actual engagement rate
                double midpoint = 50.0;
                double steepness = 0.05;
                double normalizedScore = 1.0 / (1.0 + Math.exp(-steepness * (viewsPerSub - midpoint)));

                return normalizedScore;
        }

        private double calculateActivityScore(long videoCount) {
                if (videoCount >= 1000)
                        return 1.0;
                if (videoCount >= 500)
                        return 0.9;
                if (videoCount >= 100)
                        return 0.7;
                if (videoCount >= 50)
                        return 0.5;
                if (videoCount >= 10)
                        return 0.3;
                return 0.2;
        }
}
