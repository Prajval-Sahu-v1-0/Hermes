package com.hermes.grading.scorer;

import com.hermes.domain.grading.CreatorScore;

/**
 * Computes competitiveness score as a derived market-position metric.
 * 
 * DEFINITION:
 * Competitiveness measures "how difficult it is to outperform or displace
 * this creator in their niche at the current moment."
 * 
 * FORMULA:
 * competitiveness_score = 0.40 * audience_size_score
 * + 0.35 * engagement_score
 * + 0.25 * growth_rate_score
 * 
 * INVARIANTS:
 * - Computed ONCE per SearchSession
 * - Stored in search_session_results
 * - Immutable for session lifetime
 * - No YouTube API calls during computation
 * - No recomputation during pagination/sorting
 * 
 * Pure function: no side effects, deterministic.
 */
public final class CompetitivenessScorer {

    // Fixed weights (configurable via application.properties if needed)
    private static final double WEIGHT_AUDIENCE = 0.40;
    private static final double WEIGHT_ENGAGEMENT = 0.35;
    private static final double WEIGHT_GROWTH = 0.25;

    // Bucket thresholds (view-level only, not stored)
    public static final double THRESHOLD_EMERGING = 0.20;
    public static final double THRESHOLD_GROWING = 0.40;
    public static final double THRESHOLD_ESTABLISHED = 0.60;
    public static final double THRESHOLD_DOMINANT = 0.80;

    private CompetitivenessScorer() {
    }

    /**
     * Computes competitiveness score from component scores.
     * 
     * @param audienceFit       Normalized audience size score [0,1]
     * @param engagementQuality Normalized engagement score [0,1]
     * @param growthRateScore   Normalized growth rate score [0,1]
     * @return Competitiveness score in range [0,1]
     */
    public static double compute(double audienceFit, double engagementQuality, double growthRateScore) {
        double score = (WEIGHT_AUDIENCE * audienceFit) +
                (WEIGHT_ENGAGEMENT * engagementQuality) +
                (WEIGHT_GROWTH * growthRateScore);

        // Clamp to [0, 1]
        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * Computes competitiveness from a CreatorScore.
     * Uses activityConsistency as proxy for growth rate when direct growth data
     * unavailable.
     */
    public static double computeFromScore(CreatorScore score) {
        // Use activityConsistency as growth rate proxy (active creators typically grow
        // faster)
        double growthRateProxy = score.activityConsistency();

        return compute(
                score.audienceFit(),
                score.engagementQuality(),
                growthRateProxy);
    }

    /**
     * Computes growth rate score from subscriber data.
     * 
     * growth_rate = (subscribers_now - subscribers_90_days_ago) /
     * max(subscribers_90_days_ago, 1)
     * 
     * @param currentSubscribers  Current subscriber count
     * @param previousSubscribers Subscriber count 90 days ago
     * @return Raw growth rate (not normalized)
     */
    public static double computeRawGrowthRate(long currentSubscribers, long previousSubscribers) {
        long baseline = Math.max(previousSubscribers, 1);
        return (double) (currentSubscribers - previousSubscribers) / baseline;
    }

    /**
     * Normalizes growth rate to [0,1] using sigmoid-like scaling.
     * 
     * Expected growth rates:
     * - 0% = 0.5 (neutral)
     * - 50% = ~0.75
     * - 100%+ = ~0.9+
     * - Negative = < 0.5
     */
    public static double normalizeGrowthRate(double rawGrowthRate) {
        // Sigmoid normalization: maps (-∞, +∞) to (0, 1)
        // Centered at 0 growth = 0.5, scales with growth rate
        return 1.0 / (1.0 + Math.exp(-3.0 * rawGrowthRate));
    }

    /**
     * Gets bucket label from competitiveness score.
     * Buckets are derived dynamically, NOT stored.
     * 
     * INVARIANT: Always returns a non-null bucket label.
     * Full coverage: [0.0, 1.0] maps to {Nascent, Emerging, Growing, Established,
     * Dominant}
     */
    public static String getBucket(double competitivenessScore) {
        if (competitivenessScore >= THRESHOLD_DOMINANT) {
            return "Dominant";
        } else if (competitivenessScore >= THRESHOLD_ESTABLISHED) {
            return "Established";
        } else if (competitivenessScore >= THRESHOLD_GROWING) {
            return "Growing";
        } else if (competitivenessScore >= THRESHOLD_EMERGING) {
            return "Emerging";
        }
        return "Nascent"; // Covers [0.0, 0.20) - never returns null
    }

    /**
     * Checks if score falls within a bucket.
     */
    public static boolean matchesBucket(double competitivenessScore, String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return true;
        }
        String actualBucket = getBucket(competitivenessScore);
        return bucket.equalsIgnoreCase(actualBucket);
    }
}
