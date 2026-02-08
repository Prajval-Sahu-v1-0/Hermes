package com.hermes.service.filter;

import java.util.Set;

/**
 * Maps bucket names to numeric score ranges.
 * 
 * All scores are normalized to [0.0, 1.0].
 * Buckets represent human-readable segments of the score spectrum.
 * 
 * INVARIANT: Bucket definitions are immutable and deterministic.
 */
public final class BucketMapper {

    private BucketMapper() {
    }

    /**
     * Score range for filtering.
     */
    public record ScoreRange(double min, double max) {
        public boolean contains(double score) {
            return score >= min && score < max;
        }

        /**
         * Merges two ranges into their union (covers both).
         */
        public ScoreRange union(ScoreRange other) {
            return new ScoreRange(
                    Math.min(this.min, other.min),
                    Math.max(this.max, other.max));
        }

        /**
         * Checks if score falls within this range (inclusive min, exclusive max).
         */
        public static ScoreRange ALL = new ScoreRange(0.0, 1.01);
    }

    // ===== AUDIENCE BUCKETS =====
    // Maps to audience_fit score

    public static ScoreRange audienceRange(String bucket) {
        return switch (bucket.toLowerCase()) {
            case "small" -> new ScoreRange(0.0, 0.4);
            case "medium" -> new ScoreRange(0.4, 0.7);
            case "large" -> new ScoreRange(0.7, 1.01);
            default -> ScoreRange.ALL;
        };
    }

    public static ScoreRange audienceRangeUnion(Set<String> buckets) {
        return unionRanges(buckets, BucketMapper::audienceRange);
    }

    // ===== ENGAGEMENT BUCKETS =====
    // Maps to engagement_quality score

    public static ScoreRange engagementRange(String bucket) {
        return switch (bucket.toLowerCase()) {
            case "low" -> new ScoreRange(0.0, 0.4);
            case "medium" -> new ScoreRange(0.4, 0.7);
            case "high" -> new ScoreRange(0.7, 1.01);
            default -> ScoreRange.ALL;
        };
    }

    public static ScoreRange engagementRangeUnion(Set<String> buckets) {
        return unionRanges(buckets, BucketMapper::engagementRange);
    }

    // ===== COMPETITIVENESS BUCKETS =====
    // Maps to competitiveness_score

    public static ScoreRange competitivenessRange(String bucket) {
        return switch (bucket.toLowerCase()) {
            case "nascent" -> new ScoreRange(0.0, 0.20); // NEW: covers gap
            case "emerging" -> new ScoreRange(0.20, 0.40);
            case "growing" -> new ScoreRange(0.40, 0.60);
            case "established" -> new ScoreRange(0.60, 0.80);
            case "dominant" -> new ScoreRange(0.80, 1.01);
            default -> ScoreRange.ALL;
        };
    }

    public static ScoreRange competitivenessRangeUnion(Set<String> buckets) {
        return unionRanges(buckets, BucketMapper::competitivenessRange);
    }

    // ===== ACTIVITY BUCKETS =====
    // Maps to activity_consistency score

    public static ScoreRange activityRange(String bucket) {
        return switch (bucket.toLowerCase()) {
            case "occasional" -> new ScoreRange(0.0, 0.4);
            case "consistent" -> new ScoreRange(0.4, 0.7);
            case "very_active", "very active" -> new ScoreRange(0.7, 1.01);
            default -> ScoreRange.ALL;
        };
    }

    public static ScoreRange activityRangeUnion(Set<String> buckets) {
        return unionRanges(buckets, BucketMapper::activityRange);
    }

    // ===== UTILITY =====

    @FunctionalInterface
    private interface RangeMapper {
        ScoreRange map(String bucket);
    }

    /**
     * Computes union of multiple bucket ranges (OR logic).
     * Returns the min/max that covers ALL selected buckets.
     */
    private static ScoreRange unionRanges(Set<String> buckets, RangeMapper mapper) {
        if (buckets == null || buckets.isEmpty()) {
            return ScoreRange.ALL;
        }

        double minVal = Double.MAX_VALUE;
        double maxVal = Double.MIN_VALUE;

        for (String bucket : buckets) {
            ScoreRange range = mapper.map(bucket);
            minVal = Math.min(minVal, range.min());
            maxVal = Math.max(maxVal, range.max());
        }

        return new ScoreRange(minVal, maxVal);
    }

    /**
     * Checks if a score matches ANY of the selected buckets.
     * Used when buckets are non-contiguous.
     */
    public static boolean matchesAnyBucket(double score, Set<String> buckets, RangeMapper mapper) {
        if (buckets == null || buckets.isEmpty()) {
            return true; // No filter = match all
        }
        for (String bucket : buckets) {
            if (mapper.map(bucket).contains(score)) {
                return true;
            }
        }
        return false;
    }

    // Public versions for each category
    public static boolean matchesAnyAudienceBucket(double score, Set<String> buckets) {
        return matchesAnyBucket(score, buckets, BucketMapper::audienceRange);
    }

    public static boolean matchesAnyEngagementBucket(double score, Set<String> buckets) {
        return matchesAnyBucket(score, buckets, BucketMapper::engagementRange);
    }

    public static boolean matchesAnyCompetitivenessBucket(double score, Set<String> buckets) {
        return matchesAnyBucket(score, buckets, BucketMapper::competitivenessRange);
    }

    public static boolean matchesAnyActivityBucket(double score, Set<String> buckets) {
        return matchesAnyBucket(score, buckets, BucketMapper::activityRange);
    }
}
