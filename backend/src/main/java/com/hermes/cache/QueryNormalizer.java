package com.hermes.cache;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic query normalization for cache key generation.
 * Rules applied in order:
 * 1. Lowercase
 * 2. Trim whitespace
 * 3. Remove punctuation except hyphens
 * 4. Collapse multiple spaces
 * 5. Sort words alphabetically
 * 6. Remove stopwords
 */
@Component
public class QueryNormalizer {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "for", "of", "in", "on", "to",
            "is", "are", "was", "were", "be", "been", "being", "have", "has",
            "had", "do", "does", "did", "will", "would", "could", "should",
            "may", "might", "must", "shall", "can", "need", "dare", "ought",
            "used", "with", "at", "by", "from", "as", "into", "through",
            "during", "before", "after", "above", "below", "between", "under");

    private static final String CACHE_KEY_PREFIX = "query:v1:";

    /**
     * Normalizes a query string for consistent cache key generation.
     * 
     * @param rawQuery The original user query
     * @return Normalized query string
     */
    public String normalize(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        // 1. Lowercase
        String normalized = rawQuery.toLowerCase();

        // 2. Remove punctuation except hyphens
        normalized = normalized.replaceAll("[^a-z0-9\\s-]", "");

        // 3. Collapse multiple spaces/hyphens
        normalized = normalized.replaceAll("[\\s-]+", " ");

        // 4. Trim
        normalized = normalized.trim();

        // 5. Split, remove stopwords, sort alphabetically
        String[] words = normalized.split("\\s+");
        normalized = Arrays.stream(words)
                .filter(word -> !word.isEmpty())
                .filter(word -> !STOPWORDS.contains(word))
                .sorted()
                .collect(Collectors.joining(" "));

        return normalized;
    }

    /**
     * Generates a SHA-256 digest of the normalized query.
     * 
     * @param normalizedQuery The normalized query string
     * @return First 16 characters of SHA-256 hex digest
     */
    public String digest(String normalizedQuery) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(normalizedQuery.getBytes());
            String fullHex = HexFormat.of().formatHex(hash);
            return fullHex.substring(0, 16); // First 16 chars = 64 bits
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generates the full cache key for a query.
     * 
     * @param rawQuery The original user query
     * @return Cache key in format "query:v1:{digest}"
     */
    public String getCacheKey(String rawQuery) {
        String normalized = normalize(rawQuery);
        if (normalized.isEmpty()) {
            return CACHE_KEY_PREFIX + "empty";
        }
        return CACHE_KEY_PREFIX + digest(normalized);
    }

    /**
     * Returns the normalized form for storage/logging.
     */
    public NormalizedQuery process(String rawQuery) {
        String normalized = normalize(rawQuery);
        String digestKey = getCacheKey(rawQuery);
        return new NormalizedQuery(rawQuery, normalized, digestKey);
    }

    public record NormalizedQuery(
            String original,
            String normalized,
            String digestKey) {
    }
}
