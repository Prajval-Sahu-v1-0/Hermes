package com.hermes.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hermes.domain.entity.QueryCache;
import com.hermes.repository.QueryCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Two-level cache service for query results.
 * L1: Caffeine in-memory cache (5 min TTL, 1000 entries)
 * L2: PostgreSQL persistent cache (24 hour TTL)
 */
@Service
public class QueryDigestService {

    private static final Logger log = LoggerFactory.getLogger(QueryDigestService.class);

    private final QueryNormalizer normalizer;
    private final QueryCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    // L1 Cache: In-memory
    private final Cache<String, CachedQueryResult> l1Cache;

    @Value("${hermes.cache.l2-ttl-hours:24}")
    private int l2TtlHours = 24;

    public QueryDigestService(
            QueryNormalizer normalizer,
            QueryCacheRepository cacheRepository,
            ObjectMapper objectMapper) {
        this.normalizer = normalizer;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;

        // L1 Cache configuration
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("[QueryCache] Initialized L1 cache: 1000 entries, 5min TTL");
    }

    /**
     * Attempts to retrieve cached query results.
     * Checks L1 first, then L2.
     * 
     * @param rawQuery The original user query
     * @return Optional containing cached result if found and valid
     */
    public Optional<CachedQueryResult> get(String rawQuery) {
        String cacheKey = normalizer.getCacheKey(rawQuery);

        // Check L1
        CachedQueryResult l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.debug("[QueryCache] L1 HIT for key: {}", cacheKey);
            return Optional.of(l1Result);
        }

        // Check L2
        Optional<QueryCache> l2Result = cacheRepository.findValidByDigestKey(cacheKey, Instant.now());
        if (l2Result.isPresent()) {
            log.debug("[QueryCache] L2 HIT for key: {}", cacheKey);
            QueryCache cached = l2Result.get();

            // Update hit count async
            cacheRepository.incrementHitCount(cacheKey);

            // Deserialize and promote to L1
            try {
                CachedQueryResult result = deserialize(cached);
                l1Cache.put(cacheKey, result);
                return Optional.of(result);
            } catch (JsonProcessingException e) {
                log.error("[QueryCache] Failed to deserialize L2 entry: {}", e.getMessage());
            }
        }

        log.debug("[QueryCache] MISS for key: {}", cacheKey);
        return Optional.empty();
    }

    /**
     * Stores query result in both cache levels.
     * 
     * @param rawQuery  The original user query
     * @param queries   The generated queries to cache
     * @param tokenCost Tokens consumed for this generation
     */
    public void put(String rawQuery, List<String> queries, int tokenCost) {
        QueryNormalizer.NormalizedQuery normalized = normalizer.process(rawQuery);
        String cacheKey = normalized.digestKey();

        CachedQueryResult result = new CachedQueryResult(
                normalized.normalized(),
                queries,
                tokenCost,
                Instant.now());

        // Store in L1
        l1Cache.put(cacheKey, result);

        // Store in L2
        try {
            QueryCache entity = new QueryCache();
            entity.setDigestKey(cacheKey);
            entity.setNormalizedQuery(normalized.normalized());
            entity.setResponseJson(objectMapper.writeValueAsString(queries));
            entity.setTokenCost(tokenCost);
            entity.setExpiresAt(Instant.now().plus(Duration.ofHours(l2TtlHours)));

            cacheRepository.save(entity);
            log.debug("[QueryCache] Stored in L1+L2: {}", cacheKey);
        } catch (JsonProcessingException e) {
            log.error("[QueryCache] Failed to serialize for L2: {}", e.getMessage());
        }
    }

    /**
     * Invalidates a specific cache entry.
     */
    public void invalidate(String rawQuery) {
        String cacheKey = normalizer.getCacheKey(rawQuery);
        l1Cache.invalidate(cacheKey);
        cacheRepository.deleteById(cacheKey);
        log.debug("[QueryCache] Invalidated: {}", cacheKey);
    }

    /**
     * Cleans up expired L2 entries.
     * Should be called periodically (e.g., via @Scheduled).
     */
    public int cleanupExpired() {
        int deleted = cacheRepository.deleteExpired(Instant.now());
        log.info("[QueryCache] Cleaned up {} expired L2 entries", deleted);
        return deleted;
    }

    /**
     * Returns L1 cache statistics.
     */
    public CacheStats getStats() {
        var stats = l1Cache.stats();
        return new CacheStats(
                l1Cache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate());
    }

    private CachedQueryResult deserialize(QueryCache cached) throws JsonProcessingException {
        List<String> queries = objectMapper.readValue(
                cached.getResponseJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        return new CachedQueryResult(
                cached.getNormalizedQuery(),
                queries,
                cached.getTokenCost(),
                cached.getCreatedAt());
    }

    /**
     * Cached query result record.
     */
    public record CachedQueryResult(
            String normalizedQuery,
            List<String> queries,
            int tokenCost,
            Instant cachedAt) {
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(
            long l1Size,
            long l1Hits,
            long l1Misses,
            double l1HitRate) {
    }
}
