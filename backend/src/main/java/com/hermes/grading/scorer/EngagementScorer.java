package com.hermes.grading.scorer;

import com.hermes.domain.grading.GradingCriteria.EngagementQuality;
import com.hermes.domain.grading.VideoStatistics;

import java.util.List;

/**
 * Behavior-based engagement scorer using video interaction metrics.
 * 
 * This scorer computes engagement quality based on ACTUAL USER BEHAVIOR:
 * - Likes (lightweight engagement signal)
 * - Comments (high-effort engagement signal)
 * - Views (normalization factor)
 * 
 * Unlike the legacy subscriber-ratio model, this approach:
 * - Measures real audience interaction per video
 * - Weights recent videos more heavily (recency decay)
 * - Provides a subscriber-adjusted modifier (soft, non-divisive)
 * 
 * Pure function: no DB access, no side effects, deterministic.
 */
public final class EngagementScorer {

    // Version identifier to confirm new code is running
    static {
        System.out.println("[EngagementScorer] v2.0 SIGMOID NORMALIZATION ACTIVE (steepness=3.0, midpoint=0.15)");
    }

    // =========================================================================
    // CONFIGURABLE CONSTANTS
    // =========================================================================

    /**
     * Minimum views required to consider a video for engagement calculation.
     * Videos below this threshold are ignored to avoid noise from new uploads.
     */
    public static final int MIN_VIEW_THRESHOLD = 100;

    /**
     * Maximum number of recent videos to analyze per channel.
     */
    public static final int MAX_RECENT_VIDEOS = 10;

    /**
     * Target engagement rate for normalization midpoint.
     * Using 0.15 (15%) as the "average" point for YouTube creators.
     * High-engagement genres (comedy, viral content) often exceed 20%.
     */
    public static final double ENGAGEMENT_MIDPOINT = 0.15;

    /**
     * Steepness of the sigmoid curve.
     * Lower value = more spread out scores, less saturation at extremes.
     * Using 3.0 to create meaningful differentiation even for high-engagement
     * creators.
     */
    private static final double SIGMOID_STEEPNESS = 3.0;

    /**
     * Recency weights for video engagement aggregation.
     * Newest video gets highest weight, decaying for older videos.
     */
    private static final double[] RECENCY_WEIGHTS = {
            1.00, // Video 1 (newest)
            0.85, // Video 2
            0.70, // Video 3
            0.55, // Video 4
            0.40, // Video 5+
            0.40,
            0.40,
            0.40,
            0.40,
            0.40
    };

    /**
     * Weight for behavior-based engagement in final score.
     */
    private static final double WEIGHT_ENGAGEMENT = 0.7;

    /**
     * Weight for user preference matching in final score.
     */
    private static final double WEIGHT_PREFERENCE = 0.3;

    // Engagement bucket thresholds
    private static final double THRESHOLD_HIGH = 0.75;
    private static final double THRESHOLD_MEDIUM = 0.40;

    private EngagementScorer() {
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Computes engagement score using behavior-based model.
     * 
     * @param recentVideos     List of recent video statistics (may be null/empty)
     * @param subscriberCount  Channel subscriber count
     * @param preferredQuality User's preferred engagement quality (optional)
     * @return Score in range [0.0, 1.0]
     */
    public static double score(
            List<VideoStatistics> recentVideos,
            long subscriberCount,
            EngagementQuality preferredQuality) {

        // Step 1-3: Calculate channel engagement from video interactions
        double channelEngagement = computeChannelEngagement(recentVideos);

        // Step 4: Normalize to [0, 1] using sigmoid curve (no subscriber modifier to
        // avoid inflation)
        double normalizedScore = normalizeSigmoid(channelEngagement);

        // DEBUG: Log intermediate values to diagnose scoring
        System.out.printf(
                "[EngagementScorer DEBUG] channelEngagement=%.4f, normalized=%.4f, midpoint=%.4f, videoCount=%d%n",
                channelEngagement, normalizedScore, ENGAGEMENT_MIDPOINT,
                recentVideos != null ? recentVideos.size() : 0);

        // Step 6: Combine with preference matching
        double finalScore = normalizedScore;
        if (preferredQuality != null) {
            double preferenceMatch = matchPreference(normalizedScore, preferredQuality);
            finalScore = (normalizedScore * WEIGHT_ENGAGEMENT) + (preferenceMatch * WEIGHT_PREFERENCE);
        }

        return clamp(finalScore);
    }

    /**
     * Overload for backward compatibility (channel-level stats only).
     * Falls back to a conservative estimate when video data unavailable.
     * 
     * @deprecated Use the video-based score() method for accurate engagement
     */
    @Deprecated
    public static double score(long viewCount, long subscriberCount, EngagementQuality preferredQuality) {
        // Fallback: estimate engagement rate from channel totals
        double estimatedRate = estimateEngagementRate(viewCount, subscriberCount);
        double normalizedScore = normalizeSigmoid(estimatedRate);

        if (preferredQuality != null) {
            double preferenceMatch = matchPreference(normalizedScore, preferredQuality);
            return clamp((normalizedScore * WEIGHT_ENGAGEMENT) + (preferenceMatch * WEIGHT_PREFERENCE));
        }

        return clamp(normalizedScore);
    }

    /**
     * Determines engagement bucket from final score.
     */
    public static EngagementBucket getBucket(double score) {
        if (score >= THRESHOLD_HIGH)
            return EngagementBucket.HIGH;
        if (score >= THRESHOLD_MEDIUM)
            return EngagementBucket.MEDIUM;
        return EngagementBucket.LOW;
    }

    // =========================================================================
    // STEP 1-3: CHANNEL ENGAGEMENT FROM VIDEO INTERACTIONS
    // =========================================================================

    /**
     * Computes weighted average engagement rate across recent videos.
     * 
     * For each video:
     * interactionScore = likes * 1.0 + comments * 2.0
     * engagementRate = interactionScore / views
     * 
     * Channel engagement = Σ(rate_i × weight_i) / Σ(weight_i)
     */
    private static double computeChannelEngagement(List<VideoStatistics> videos) {
        if (videos == null || videos.isEmpty()) {
            System.out.println("[EngagementScorer DEBUG] No video data available");
            return 0.0; // No video data available
        }

        double weightedSum = 0.0;
        double weightSum = 0.0;

        int limit = Math.min(videos.size(), MAX_RECENT_VIDEOS);
        for (int i = 0; i < limit; i++) {
            VideoStatistics video = videos.get(i);
            double rate = video.engagementRate(MIN_VIEW_THRESHOLD);

            // DEBUG: Print per-video stats
            System.out.printf("[EngagementScorer DEBUG] Video %d: views=%d, likes=%d, comments=%d, rate=%.4f%n",
                    i, video.viewCount(), video.likeCount(), video.commentCount(), rate);

            if (rate > 0) { // Only include videos that passed threshold
                double weight = getRecencyWeight(i);
                weightedSum += rate * weight;
                weightSum += weight;
            }
        }

        if (weightSum == 0) {
            System.out.println("[EngagementScorer DEBUG] No valid videos (all below threshold)");
            return 0.0; // No valid videos
        }

        double result = weightedSum / weightSum;
        System.out.printf("[EngagementScorer DEBUG] Channel engagement = %.4f (from %d videos)%n", result, limit);
        return result;
    }

    /**
     * Gets recency weight for video at given index (0 = newest).
     */
    private static double getRecencyWeight(int index) {
        if (index < RECENCY_WEIGHTS.length) {
            return RECENCY_WEIGHTS[index];
        }
        return RECENCY_WEIGHTS[RECENCY_WEIGHTS.length - 1]; // Use minimum weight
    }

    // =========================================================================
    // STEP 4: SUBSCRIBER STABILITY MODIFIER
    // =========================================================================

    /**
     * Applies subscriber-based modifier to engagement.
     * 
     * Formula: adjustedEngagement = channelEngagement × (1 + 1/log₁₀(subs + 1))
     * 
     * This provides a SOFT boost, not a divisive penalty:
     * - Small channels (1K subs): 1.33x multiplier
     * - Medium channels (100K subs): 1.20x multiplier
     * - Large channels (1M subs): 1.17x multiplier
     * 
     * Rationale: Smaller channels naturally have higher engagement rates,
     * so we normalize across scales without penalizing them.
     */
    private static double applySubscriberModifier(double channelEngagement, long subscriberCount) {
        if (subscriberCount <= 0) {
            return channelEngagement; // No modification for unknown subscriber count
        }

        double subscriberFactor = Math.log10(subscriberCount + 1);
        if (subscriberFactor <= 0) {
            return channelEngagement;
        }

        double modifier = 1.0 + (1.0 / subscriberFactor);
        return channelEngagement * modifier;
    }

    // =========================================================================
    // STEP 5: NORMALIZATION
    // =========================================================================

    /**
     * Normalizes engagement to [0, 1] using a sigmoid curve.
     * 
     * This creates a smooth distribution where:
     * - engagementRate = 0 → score ≈ 0.02
     * - engagementRate = MIDPOINT → score = 0.5
     * - engagementRate = 2×MIDPOINT → score ≈ 0.85
     * - Very high engagement → approaches 1.0 asymptotically
     * 
     * This prevents the "everyone is 100%" problem by never capping at 1.0.
     */
    private static double normalizeSigmoid(double engagementRate) {
        if (engagementRate <= 0) {
            return 0.0;
        }
        // Sigmoid: 1 / (1 + e^(-k*(x - midpoint)))
        // Shifted so midpoint gives 0.5
        double exponent = -SIGMOID_STEEPNESS * (engagementRate - ENGAGEMENT_MIDPOINT);
        return 1.0 / (1.0 + Math.exp(exponent));
    }

    /**
     * DEPRECATED: Old linear normalization that caused 100% bug.
     */
    @SuppressWarnings("unused")
    private static double normalizeLinear(double adjustedEngagement) {
        if (adjustedEngagement <= 0) {
            return 0.0;
        }
        return Math.min(adjustedEngagement / ENGAGEMENT_MIDPOINT, 1.0);
    }

    // =========================================================================
    // STEP 6: PREFERENCE MATCHING
    // =========================================================================

    /**
     * Calculates how well the engagement score matches user preference.
     */
    private static double matchPreference(double normalizedScore, EngagementQuality preference) {
        double min = preference.getMinRatio(); // Now these are normalized thresholds
        double max = preference.getMaxRatio();

        if (normalizedScore >= min && normalizedScore < max) {
            return 1.0; // Perfect match
        }

        // Distance-based penalty
        double distance;
        if (normalizedScore < min) {
            distance = (min - normalizedScore) / min;
        } else {
            distance = (normalizedScore - max) / (1.0 - max + 0.01); // Avoid division by zero
        }

        return Math.max(0.0, 1.0 - distance) * 0.7;
    }

    // =========================================================================
    // FALLBACK LOGIC (Legacy Compatibility)
    // =========================================================================

    /**
     * Estimates engagement rate when video data is unavailable.
     * Uses a conservative estimate based on industry averages.
     */
    private static double estimateEngagementRate(long viewCount, long subscriberCount) {
        if (subscriberCount == 0 || viewCount == 0) {
            return 0.0;
        }

        // Conservative estimate: assume 4% like rate, 0.7% comment rate
        // Combined: (0.04 * 1.0) + (0.007 * 2.0) = 0.054
        double conservativeRate = 0.05;

        // Apply subscriber modifier
        return applySubscriberModifier(conservativeRate, subscriberCount);
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    /**
     * Engagement bucket classification.
     */
    public enum EngagementBucket {
        HIGH,
        MEDIUM,
        LOW
    }
}
