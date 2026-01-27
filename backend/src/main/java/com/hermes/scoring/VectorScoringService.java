package com.hermes.scoring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hermes.cache.QueryNormalizer;
import com.hermes.domain.entity.Creator;
import com.hermes.domain.entity.QueryEmbedding;
import com.hermes.domain.grading.GradedCreator;
import com.hermes.domain.grading.CreatorScore;
import com.hermes.ingestion.EmbeddingService;
import com.hermes.repository.CreatorRepository;
import com.hermes.repository.QueryEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Vector-based scoring service.
 * Replaces text-based LLM grading with cosine similarity.
 * Uses cached embeddings for queries and creators.
 */
@Service
public class VectorScoringService {

    private static final Logger log = LoggerFactory.getLogger(VectorScoringService.class);
    private static final String EMBED_MODEL = "embed-english-v3.0";
    private static final int EMBEDDING_TTL_DAYS = 7;

    private final EmbeddingService embeddingService;
    private final QueryNormalizer queryNormalizer;
    private final QueryEmbeddingRepository queryEmbeddingRepository;
    private final CreatorRepository creatorRepository;

    // L1 cache for query embeddings
    private final Cache<String, double[]> queryEmbeddingCache;

    public VectorScoringService(
            EmbeddingService embeddingService,
            QueryNormalizer queryNormalizer,
            QueryEmbeddingRepository queryEmbeddingRepository,
            CreatorRepository creatorRepository) {
        this.embeddingService = embeddingService;
        this.queryNormalizer = queryNormalizer;
        this.queryEmbeddingRepository = queryEmbeddingRepository;
        this.creatorRepository = creatorRepository;

        // L1 cache for query embeddings
        this.queryEmbeddingCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Scores creators against a query using vector similarity.
     * 
     * @param query      The search query
     * @param channelIds List of channel IDs to score
     * @return Map of channelId to similarity score [0.0, 1.0]
     */
    public Map<String, Double> scoreCreators(String query, List<String> channelIds) {
        if (channelIds.isEmpty()) {
            return Map.of();
        }

        // Get query embedding (cached)
        double[] queryEmbedding = getQueryEmbedding(query);
        if (queryEmbedding == null || isZeroVector(queryEmbedding)) {
            log.warn("[VectorScoring] No query embedding available, using fallback scoring");
            return fallbackScoring(query, channelIds);
        }

        // Fetch creator embeddings
        List<Creator> creators = creatorRepository.findAllById(
                channelIds.stream()
                        .map(id -> creatorRepository.findByPlatformAndChannelId("youtube", id))
                        .filter(Optional::isPresent)
                        .map(opt -> opt.get().getId())
                        .toList());

        Map<String, Double> scores = new HashMap<>();
        for (Creator creator : creators) {
            double[] creatorEmbedding = creator.getProfileEmbedding();
            if (creatorEmbedding != null && !isZeroVector(creatorEmbedding)) {
                double similarity = embeddingService.cosineSimilarity(queryEmbedding, creatorEmbedding);
                // Normalize to [0, 1] range (cosine can be negative)
                double normalizedScore = (similarity + 1.0) / 2.0;
                scores.put(creator.getChannelId(), normalizedScore);
            } else {
                // Fallback for creators without embeddings
                scores.put(creator.getChannelId(), 0.5);
            }
        }

        return scores;
    }

    /**
     * Ranks and grades creators using vector similarity.
     * 
     * @param query    The search query
     * @param creators List of creators to rank
     * @return Sorted list of GradedCreators
     */
    public List<GradedCreator> rankCreators(String query, List<Creator> creators) {
        if (creators.isEmpty()) {
            return List.of();
        }

        double[] queryEmbedding = getQueryEmbedding(query);
        String normalizedQuery = queryNormalizer.normalize(query);

        List<ScoredCreator> scoredCreators = new ArrayList<>();
        for (Creator creator : creators) {
            double vectorScore = 0.5; // Default

            if (queryEmbedding != null && !isZeroVector(queryEmbedding)) {
                double[] creatorEmbedding = creator.getProfileEmbedding();
                if (creatorEmbedding != null && !isZeroVector(creatorEmbedding)) {
                    vectorScore = (embeddingService.cosineSimilarity(queryEmbedding, creatorEmbedding) + 1.0) / 2.0;
                }
            }

            // Combine with name match boost
            double nameMatchBoost = calculateNameMatchBoost(creator.getChannelName(), normalizedQuery);
            double finalScore = vectorScore * 0.7 + nameMatchBoost * 0.3;

            scoredCreators.add(new ScoredCreator(creator, finalScore, vectorScore, nameMatchBoost));
        }

        // Sort by score descending
        scoredCreators.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        // Convert to GradedCreators
        return scoredCreators.stream()
                .map(this::toGradedCreator)
                .toList();
    }

    /**
     * Gets or creates query embedding.
     */
    private double[] getQueryEmbedding(String query) {
        String cacheKey = queryNormalizer.getCacheKey(query);

        // Check L1 cache
        double[] cached = queryEmbeddingCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Check L2 (database)
        Optional<QueryEmbedding> dbCached = queryEmbeddingRepository.findValidByDigestKey(cacheKey, Instant.now());
        if (dbCached.isPresent()) {
            double[] embedding = dbCached.get().getEmbedding();
            queryEmbeddingCache.put(cacheKey, embedding);
            return embedding;
        }

        // Generate new embedding
        String normalized = queryNormalizer.normalize(query);
        double[] embedding = embeddingService.embed(normalized, EmbeddingService.InputType.SEARCH_QUERY);

        // Store in L2
        QueryEmbedding entity = new QueryEmbedding();
        entity.setDigestKey(cacheKey);
        entity.setNormalizedQuery(normalized);
        entity.setEmbedding(embedding);
        entity.setModelVersion(EMBED_MODEL);
        entity.setExpiresAt(Instant.now().plus(Duration.ofDays(EMBEDDING_TTL_DAYS)));
        queryEmbeddingRepository.save(entity);

        // Store in L1
        queryEmbeddingCache.put(cacheKey, embedding);

        return embedding;
    }

    /**
     * Fallback scoring when embeddings are unavailable.
     */
    private Map<String, Double> fallbackScoring(String query, List<String> channelIds) {
        String normalized = queryNormalizer.normalize(query);
        Map<String, Double> scores = new HashMap<>();

        for (String channelId : channelIds) {
            Optional<Creator> creatorOpt = creatorRepository.findByPlatformAndChannelId("youtube", channelId);
            if (creatorOpt.isPresent()) {
                Creator creator = creatorOpt.get();
                double nameMatch = calculateNameMatchBoost(creator.getChannelName(), normalized);
                scores.put(channelId, nameMatch);
            } else {
                scores.put(channelId, 0.3);
            }
        }

        return scores;
    }

    /**
     * Calculates name match boost [0.0, 1.0].
     */
    private double calculateNameMatchBoost(String channelName, String normalizedQuery) {
        if (channelName == null || normalizedQuery.isEmpty()) {
            return 0.3;
        }

        String cleanChannel = channelName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String cleanQuery = normalizedQuery.replaceAll("[^a-z0-9]", "");

        if (cleanChannel.equals(cleanQuery)) {
            return 1.0;
        }
        if (cleanChannel.startsWith(cleanQuery) || cleanQuery.startsWith(cleanChannel)) {
            return 0.9;
        }
        if (cleanChannel.contains(cleanQuery) || cleanQuery.contains(cleanChannel)) {
            return 0.7;
        }

        // Word overlap
        Set<String> channelWords = new HashSet<>(Arrays.asList(cleanChannel.split("\\s+")));
        Set<String> queryWords = new HashSet<>(Arrays.asList(normalizedQuery.split("\\s+")));
        channelWords.retainAll(queryWords);
        if (!channelWords.isEmpty()) {
            return 0.5 + 0.2 * channelWords.size() / queryWords.size();
        }

        return 0.3;
    }

    private boolean isZeroVector(double[] vector) {
        for (double v : vector) {
            if (v != 0.0)
                return false;
        }
        return true;
    }

    private GradedCreator toGradedCreator(ScoredCreator scored) {
        Creator creator = scored.creator;

        // Generate labels based on scores
        List<String> labels = new ArrayList<>();
        if (scored.nameMatchBoost >= 0.9) {
            labels.add("Best Match");
        } else if (scored.vectorScore >= 0.7) {
            labels.add("Strong Fit");
        }
        if (scored.finalScore >= 0.8) {
            labels.add("Highly Relevant");
        }
        if (labels.isEmpty()) {
            labels.add("Related");
        }

        // Create score breakdown
        CreatorScore score = CreatorScore.compute(
                scored.vectorScore, // repurposed as genreRelevance
                0.7, // default audience
                0.7, // default engagement
                0.7, // default activity
                1.0 // fresh
        );

        return new GradedCreator(
                creator.getChannelId(),
                creator.getChannelName(),
                creator.getDescription(),
                creator.getProfileImageUrl(),
                creator.getPlatform(),
                score,
                labels);
    }

    private record ScoredCreator(
            Creator creator,
            double finalScore,
            double vectorScore,
            double nameMatchBoost) {
    }
}
