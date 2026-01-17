package com.hermes.grading.scorer;

import com.hermes.domain.grading.GradingCriteria.EngagementQuality;

/**
 * Scores engagement quality based on views-to-subscriber ratio.
 * Higher ratio indicates more engaged audience.
 * 
 * Pure function: no DB access, no side effects, deterministic.
 */
public final class EngagementScorer {

    // Normalization thresholds for views-per-subscriber ratio
    private static final double LOW_THRESHOLD = 50.0;
    private static final double HIGH_THRESHOLD = 500.0;

    private EngagementScorer() {
    }

    /**
     * Computes engagement quality score based on views/subscriber ratio.
     * 
     * @param viewCount        Total channel views
     * @param subscriberCount  Total subscribers
     * @param preferredQuality User's preferred engagement quality (optional)
     * @return Score in range [0.0, 1.0]
     */
    public static double score(long viewCount, long subscriberCount, EngagementQuality preferredQuality) {
        if (subscriberCount == 0) {
            return 0.0; // Can't calculate engagement without subscribers
        }

        double ratio = (double) viewCount / subscriberCount;

        // Normalize to [0, 1] using log scale
        double normalizedScore = normalizeRatio(ratio);

        // If user has a preference, boost/penalize based on match
        if (preferredQuality != null) {
            double preferenceMatch = matchPreference(ratio, preferredQuality);
            normalizedScore = (normalizedScore + preferenceMatch) / 2.0;
        }

        return Math.min(1.0, Math.max(0.0, normalizedScore));
    }

    /**
     * Normalizes views/subscriber ratio to [0, 1] using log scale.
     */
    private static double normalizeRatio(double ratio) {
        if (ratio <= 0)
            return 0.0;
        if (ratio >= HIGH_THRESHOLD)
            return 1.0;
        if (ratio <= LOW_THRESHOLD)
            return ratio / LOW_THRESHOLD * 0.5;

        // Log scale for middle range
        double logRatio = Math.log(ratio / LOW_THRESHOLD);
        double logMax = Math.log(HIGH_THRESHOLD / LOW_THRESHOLD);
        return 0.5 + (logRatio / logMax) * 0.5;
    }

    /**
     * Calculates how well the ratio matches user preference.
     */
    private static double matchPreference(double ratio, EngagementQuality preference) {
        double min = preference.getMinRatio();
        double max = preference.getMaxRatio();

        if (ratio >= min && ratio < max) {
            return 1.0; // Perfect match
        }

        // Calculate distance-based penalty
        double distance;
        if (ratio < min) {
            distance = (min - ratio) / min;
        } else {
            distance = (ratio - max) / max;
        }

        return Math.max(0.0, 1.0 - distance) * 0.7;
    }
}
