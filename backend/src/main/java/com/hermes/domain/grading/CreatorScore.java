package com.hermes.domain.grading;

/**
 * Scoring breakdown for a creator.
 * All scores are normalized to [0.0, 1.0] range.
 */
public record CreatorScore(
        double genreRelevance,
        double audienceFit,
        double engagementQuality,
        double activityConsistency,
        double freshness,
        double finalScore) {
    // Scoring weights
    public static final double WEIGHT_GENRE = 0.35;
    public static final double WEIGHT_AUDIENCE = 0.20;
    public static final double WEIGHT_ENGAGEMENT = 0.20;
    public static final double WEIGHT_ACTIVITY = 0.15;
    public static final double WEIGHT_FRESHNESS = 0.10;

    /**
     * Creates a CreatorScore with computed final score from sub-scores.
     */
    public static CreatorScore compute(
            double genreRelevance,
            double audienceFit,
            double engagementQuality,
            double activityConsistency,
            double freshness) {

        double finalScore = genreRelevance * WEIGHT_GENRE +
                audienceFit * WEIGHT_AUDIENCE +
                engagementQuality * WEIGHT_ENGAGEMENT +
                activityConsistency * WEIGHT_ACTIVITY +
                freshness * WEIGHT_FRESHNESS;

        return new CreatorScore(
                genreRelevance,
                audienceFit,
                engagementQuality,
                activityConsistency,
                freshness,
                Math.min(1.0, Math.max(0.0, finalScore)) // Clamp to [0, 1]
        );
    }
}
