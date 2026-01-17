package com.hermes.grading.scorer;

import com.hermes.domain.grading.GradingCriteria.AudienceScale;

/**
 * Scores audience fit based on subscriber count matching
 * the user's preferred audience scale.
 * 
 * Pure function: no DB access, no side effects, deterministic.
 */
public final class AudienceFitScorer {

    private AudienceFitScorer() {
    }

    /**
     * Computes audience fit score based on subscriber count.
     * 
     * @param subscriberCount The channel's subscriber count
     * @param preferredScale  The user's preferred audience scale
     * @return Score in range [0.0, 1.0]
     */
    public static double score(long subscriberCount, AudienceScale preferredScale) {
        if (preferredScale == null) {
            return 0.5; // Neutral score if no preference
        }

        // Perfect match
        if (preferredScale.matches(subscriberCount)) {
            return 1.0;
        }

        // Calculate distance-based score for near misses
        long min = preferredScale.getMinSubscribers();
        long max = preferredScale.getMaxSubscribers();
        long rangeCenter = (min + max) / 2;

        // For LARGE scale, use a reasonable center
        if (max == Long.MAX_VALUE) {
            rangeCenter = min * 10; // e.g., 1M for LARGE
        }

        // Calculate how far outside the range
        double distance;
        if (subscriberCount < min) {
            distance = (double) (min - subscriberCount) / min;
        } else {
            // subscriberCount > max
            distance = (double) (subscriberCount - max) / max;
        }

        // Convert distance to score (closer = higher)
        double score = Math.max(0.0, 1.0 - distance);

        // Penalty for being outside the range
        return score * 0.7; // Max 0.7 if outside range
    }
}
