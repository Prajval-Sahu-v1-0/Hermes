package com.hermes.grading.scorer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores genre relevance based on keyword overlap between
 * the search genre and channel title/description.
 * 
 * Pure function: no DB access, no side effects, deterministic.
 */
public final class GenreRelevanceScorer {

    private GenreRelevanceScorer() {
    }

    /**
     * Computes genre relevance score based on keyword matching.
     * 
     * @param channelName The channel name/title
     * @param description The channel description
     * @param baseGenre   The user's search genre
     * @return Score in range [0.0, 1.0]
     */
    public static double score(String channelName, String description, String baseGenre) {
        if (baseGenre == null || baseGenre.isBlank()) {
            return 0.5; // Neutral score if no genre specified
        }

        Set<String> genreKeywords = tokenize(baseGenre);
        if (genreKeywords.isEmpty()) {
            return 0.5;
        }

        String combinedText = normalize(
                (channelName != null ? channelName : "") + " " +
                        (description != null ? description : ""));

        // Count matching keywords
        int matches = 0;
        for (String keyword : genreKeywords) {
            if (combinedText.contains(keyword)) {
                matches++;
            }
        }

        // Calculate base score from keyword overlap
        double overlapRatio = (double) matches / genreKeywords.size();

        // Apply boost for exact genre match in channel name
        if (channelName != null && normalize(channelName).contains(normalize(baseGenre))) {
            overlapRatio = Math.min(1.0, overlapRatio + 0.3);
        }

        return Math.min(1.0, overlapRatio);
    }

    /**
     * Tokenizes text into lowercase keywords.
     */
    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalize(text).split("\\s+"))
                .filter(s -> s.length() > 2) // Skip very short words
                .collect(Collectors.toSet());
    }

    /**
     * Normalizes text for comparison.
     */
    private static String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
