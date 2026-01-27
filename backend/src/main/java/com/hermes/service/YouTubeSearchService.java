package com.hermes.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.hermes.domain.dto.QueryResultsMap;
import com.hermes.domain.model.CreatorProfile;
import com.hermes.governor.YouTubeQuotaGovernor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Phase Two: YouTube Channel Search Service.
 * 
 * Features:
 * - Channel metadata L1 cache (avoid redundant channels.list calls)
 * - Query deduplication (avoid redundant search.list calls)
 * - YouTubeQuotaGovernor integration (quota-aware execution)
 * - Multiple API key rotation
 */
@Service
public class YouTubeSearchService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSearchService.class);

    private final List<String> apiKeys;
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);
    private final YouTube youtube;
    private final YouTubeQuotaGovernor quotaGovernor;

    // L1 cache for channel metadata (avoid refetching known channels)
    private final Cache<String, CachedChannel> channelCache;

    @Value("${hermes.youtube.max-queries-per-search:5}")
    private int maxQueriesPerSearch;

    @Value("${hermes.youtube.max-results-per-query:50}")
    private int maxResultsPerQuery;

    public YouTubeSearchService(
            @Value("${hermes.youtube.api-keys}") String apiKeysConfig,
            YouTubeQuotaGovernor quotaGovernor) throws GeneralSecurityException, IOException {

        this.quotaGovernor = quotaGovernor;

        // Parse comma-separated API keys
        this.apiKeys = Arrays.stream(apiKeysConfig.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one YouTube API key must be configured");
        }

        log.info("[YouTubeSearch] Initialized with {} API key(s) for rotation", apiKeys.size());

        this.youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName("hermes-backend")
                .build();

        // L1 Channel cache: 2000 channels, 1 hour TTL
        this.channelCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();

        log.info("[YouTubeSearch] Channel cache initialized: 2000 entries, 1h TTL");
    }

    /**
     * Searches YouTube for channels using each query independently.
     * 
     * @param queries       List of search queries from Phase One
     * @param limitPerQuery Maximum number of channels to fetch per query (max 50)
     * @return QueryResultsMap preserving query-to-results association
     */
    public QueryResultsMap searchChannels(List<String> queries, int limitPerQuery) {
        // Step 1: Check quota before starting
        int estimatedCost = quotaGovernor.estimateCost(queries.size(), limitPerQuery);
        var quotaDecision = quotaGovernor.checkQuota(estimatedCost);

        if (!quotaDecision.isAllowed()) {
            log.warn("[YouTubeSearch] Quota exhausted, returning empty results");
            return QueryResultsMap.of(Map.of());
        }

        // Step 2: Apply quota-based limits
        int effectiveMaxQueries = Math.min(queries.size(), quotaDecision.getMaxQueries());
        int effectiveMaxResults = Math.min(limitPerQuery, quotaDecision.getMaxResults());
        effectiveMaxResults = Math.min(effectiveMaxResults, 50); // YouTube API max

        // Step 3: Deduplicate queries
        List<String> uniqueQueries = deduplicateQueries(queries).stream()
                .limit(effectiveMaxQueries)
                .toList();

        log.info("[YouTubeSearch] Executing {} unique queries (from {} original), max {} results each",
                uniqueQueries.size(), queries.size(), effectiveMaxResults);

        // Step 4: Execute searches
        Map<String, List<CreatorProfile>> results = new LinkedHashMap<>();
        int totalQuotaUsed = 0;

        for (String query : uniqueQueries) {
            log.debug("[YouTubeSearch] Searching for: {}", query);
            SearchResultWithQuota searchResult = searchForQueryWithRetry(query, effectiveMaxResults);
            results.put(query, searchResult.profiles());
            totalQuotaUsed += searchResult.quotaUsed();
            log.info("[YouTubeSearch] Found {} channels for: {} (quota: {})",
                    searchResult.profiles().size(), query, searchResult.quotaUsed());
        }

        // Step 5: Record quota usage
        quotaGovernor.recordUsage(totalQuotaUsed);
        log.info("[YouTubeSearch] Total quota used: {} units", totalQuotaUsed);

        return QueryResultsMap.of(results);
    }

    /**
     * Deduplicates queries (case-insensitive, trimmed).
     */
    private List<String> deduplicateQueries(List<String> queries) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> unique = new ArrayList<>();
        for (String query : queries) {
            String normalized = query.toLowerCase().trim();
            if (seen.add(normalized)) {
                unique.add(query);
            }
        }
        return unique;
    }

    /**
     * Gets the current API key.
     */
    private String getCurrentApiKey() {
        return apiKeys.get(currentKeyIndex.get() % apiKeys.size());
    }

    /**
     * Rotates to the next API key and returns it.
     */
    private String rotateToNextKey() {
        int nextIndex = currentKeyIndex.incrementAndGet() % apiKeys.size();
        log.info("[YouTubeSearch] Rotated to API key #{} of {}", nextIndex + 1, apiKeys.size());
        return apiKeys.get(nextIndex);
    }

    /**
     * Searches for a query with automatic API key rotation on quota errors.
     */
    private SearchResultWithQuota searchForQueryWithRetry(String query, int limit) {
        int maxAttempts = apiKeys.size(); // Try each key once

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String apiKey = getCurrentApiKey();
            try {
                return searchForQuery(query, limit, apiKey);
            } catch (IOException e) {
                // Check if it's a quota error
                if (isQuotaError(e)) {
                    log.warn("[YouTubeSearch] Quota exceeded for key #{}, rotating to next key...",
                            currentKeyIndex.get() + 1);
                    rotateToNextKey();
                } else {
                    log.error("[YouTubeSearch] API Error for query '{}': {}", query, e.getMessage());
                    return new SearchResultWithQuota(Collections.emptyList(), 0);
                }
            }
        }

        log.error("[YouTubeSearch] All API keys exhausted for query: {}", query);
        return new SearchResultWithQuota(Collections.emptyList(), 0);
    }

    /**
     * Checks if an IOException is a quota exceeded error.
     */
    private boolean isQuotaError(IOException e) {
        String message = e.getMessage();
        if (message == null)
            return false;
        return message.contains("quotaExceeded") ||
                message.contains("dailyLimitExceeded") ||
                message.contains("rateLimitExceeded") ||
                message.contains("403");
    }

    /**
     * Searches YouTube for channels matching a single query.
     * Uses channel cache to avoid redundant channels.list calls.
     * 
     * @param query  The search query
     * @param limit  Maximum number of results (1-50)
     * @param apiKey The API key to use
     * @return SearchResultWithQuota containing profiles and quota used
     */
    private SearchResultWithQuota searchForQuery(String query, int limit, String apiKey) throws IOException {
        int quotaUsed = 0;

        // 1. Search for channels (100 units)
        YouTube.Search.List search = youtube.search().list(List.of("snippet"));
        search.setKey(apiKey);
        search.setQ(query);
        search.setType(List.of("channel"));
        search.setMaxResults((long) limit);

        SearchListResponse searchResponse = search.execute();
        quotaUsed += YouTubeQuotaGovernor.SEARCH_LIST_COST;

        List<SearchResult> searchResults = searchResponse.getItems();

        if (searchResults == null || searchResults.isEmpty()) {
            return new SearchResultWithQuota(Collections.emptyList(), quotaUsed);
        }

        // 2. Extract channel IDs
        List<String> allChannelIds = searchResults.stream()
                .map(r -> r.getSnippet().getChannelId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (allChannelIds.isEmpty()) {
            return new SearchResultWithQuota(Collections.emptyList(), quotaUsed);
        }

        // 3. Check cache for known channels
        List<CreatorProfile> cachedProfiles = new ArrayList<>();
        List<String> uncachedChannelIds = new ArrayList<>();

        for (String channelId : allChannelIds) {
            CachedChannel cached = channelCache.getIfPresent(channelId);
            if (cached != null) {
                cachedProfiles.add(cached.toProfile());
            } else {
                uncachedChannelIds.add(channelId);
            }
        }

        log.debug("[YouTubeSearch] Cache hit: {}, miss: {} for query: {}",
                cachedProfiles.size(), uncachedChannelIds.size(), query);

        // 4. Fetch only uncached channels from API
        List<CreatorProfile> freshProfiles = new ArrayList<>();
        if (!uncachedChannelIds.isEmpty()) {
            YouTube.Channels.List channelList = youtube.channels().list(List.of("snippet", "statistics"));
            channelList.setKey(apiKey);
            channelList.setId(uncachedChannelIds);

            ChannelListResponse channelResponse = channelList.execute();
            quotaUsed += YouTubeQuotaGovernor.CHANNELS_LIST_COST_PER_CALL;

            List<Channel> channels = channelResponse.getItems();
            if (channels != null) {
                for (Channel channel : channels) {
                    CreatorProfile profile = mapToProfile(channel);
                    freshProfiles.add(profile);

                    // Cache the channel
                    CachedChannel cached = CachedChannel.fromChannel(channel);
                    channelCache.put(channel.getId(), cached);
                }
            }
        }

        // 5. Combine cached + fresh profiles
        List<CreatorProfile> allProfiles = new ArrayList<>(cachedProfiles);
        allProfiles.addAll(freshProfiles);

        return new SearchResultWithQuota(allProfiles, quotaUsed);
    }

    /**
     * Maps a YouTube Channel to our internal CreatorProfile domain model.
     */
    private CreatorProfile mapToProfile(Channel channel) {
        var snippet = channel.getSnippet();
        var stats = channel.getStatistics();

        long subscribers = 0;
        long videos = 0;
        long views = 0;

        if (stats != null) {
            subscribers = stats.getSubscriberCount() != null ? stats.getSubscriberCount().longValue() : 0;
            videos = stats.getVideoCount() != null ? stats.getVideoCount().longValue() : 0;
            views = stats.getViewCount() != null ? stats.getViewCount().longValue() : 0;
        }

        String avatarUrl = "";
        if (snippet.getThumbnails() != null) {
            // Prefer high > medium > default for better quality
            var thumbs = snippet.getThumbnails();
            if (thumbs.getHigh() != null) {
                avatarUrl = thumbs.getHigh().getUrl();
            } else if (thumbs.getMedium() != null) {
                avatarUrl = thumbs.getMedium().getUrl();
            } else if (thumbs.getDefault() != null) {
                avatarUrl = thumbs.getDefault().getUrl();
            }
        }

        return new CreatorProfile(
                channel.getId(),
                snippet.getCustomUrl(),
                snippet.getTitle(),
                snippet.getDescription(),
                avatarUrl,
                subscribers,
                videos,
                views,
                List.of(),
                snippet.getCountry(),
                new HashMap<>(),
                0.0);
    }

    /**
     * Returns channel cache statistics.
     */
    public ChannelCacheStats getCacheStats() {
        var stats = channelCache.stats();
        return new ChannelCacheStats(
                channelCache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate());
    }

    /**
     * Clears the channel cache to force fresh data fetch.
     * 
     * @return Number of entries that were cleared
     */
    public long clearCache() {
        long size = channelCache.estimatedSize();
        channelCache.invalidateAll();
        log.info("[YouTubeSearch] Channel cache cleared: {} entries removed", size);
        return size;
    }

    // ===== Records =====

    /**
     * Search result with quota tracking.
     */
    record SearchResultWithQuota(List<CreatorProfile> profiles, int quotaUsed) {
    }

    /**
     * Cached channel metadata to avoid redundant channels.list calls.
     */
    record CachedChannel(
            String channelId,
            String channelName,
            String description,
            String avatarUrl,
            long subscribers,
            long videos,
            long views,
            String country,
            Instant cachedAt) {

        static CachedChannel fromChannel(Channel channel) {
            var snippet = channel.getSnippet();
            var stats = channel.getStatistics();

            long subs = 0, vids = 0, viewCount = 0;
            if (stats != null) {
                subs = stats.getSubscriberCount() != null ? stats.getSubscriberCount().longValue() : 0;
                vids = stats.getVideoCount() != null ? stats.getVideoCount().longValue() : 0;
                viewCount = stats.getViewCount() != null ? stats.getViewCount().longValue() : 0;
            }

            String avatar = "";
            if (snippet.getThumbnails() != null) {
                // Prefer high > medium > default for better quality
                var thumbs = snippet.getThumbnails();
                if (thumbs.getHigh() != null) {
                    avatar = thumbs.getHigh().getUrl();
                } else if (thumbs.getMedium() != null) {
                    avatar = thumbs.getMedium().getUrl();
                } else if (thumbs.getDefault() != null) {
                    avatar = thumbs.getDefault().getUrl();
                }
            }

            return new CachedChannel(
                    channel.getId(),
                    snippet.getTitle(),
                    snippet.getDescription(),
                    avatar,
                    subs,
                    vids,
                    viewCount,
                    snippet.getCountry(),
                    Instant.now());
        }

        CreatorProfile toProfile() {
            return new CreatorProfile(
                    channelId,
                    null,
                    channelName,
                    description,
                    avatarUrl,
                    subscribers,
                    videos,
                    views,
                    List.of(),
                    country,
                    new HashMap<>(),
                    0.0);
        }
    }

    /**
     * Channel cache statistics.
     */
    public record ChannelCacheStats(
            long cacheSize,
            long hits,
            long misses,
            double hitRate) {
    }
}
