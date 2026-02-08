package com.hermes.domain.dto;

import java.util.Set;

/**
 * Multi-select filter criteria for session result filtering.
 * 
 * FILTERING RULES:
 * - OR logic within the same category (union)
 * - AND logic across different categories (intersection)
 * 
 * INVARIANTS:
 * - Filters operate on precomputed, stored statistics ONLY
 * - NO API calls, NO LLM calls
 * - NO recomputation, NO re-ranking
 * - Session-scoped, read-only operations
 * 
 * All filter fields are optional. Null/empty means "no filter" for that
 * category.
 */
public record FilterCriteria(
        /**
         * Audience size buckets (multi-select).
         * Values: "small", "medium", "large"
         * Backed by: audience_fit score
         */
        Set<String> audience,

        /**
         * Engagement level buckets (multi-select).
         * Values: "low", "medium", "high"
         * Backed by: engagement_quality score
         */
        Set<String> engagement,

        /**
         * Competitiveness tier buckets (multi-select).
         * Values: "nascent", "emerging", "growing", "established", "dominant"
         * Backed by: competitiveness_score
         */
        Set<String> competitiveness,

        /**
         * Activity level buckets (multi-select).
         * Values: "occasional", "consistent", "very_active"
         * Backed by: activity_consistency score
         */
        Set<String> activity,

        /**
         * Platform filter (multi-select).
         * Values: "youtube", "tiktok", etc.
         */
        Set<String> platform,

        /**
         * Genre/category labels (multi-select).
         * Values: any genre label strings
         */
        Set<String> genres) {
    /**
     * Creates an empty filter (no constraints).
     */
    public static FilterCriteria empty() {
        return new FilterCriteria(null, null, null, null, null, null);
    }

    /**
     * Checks if any filters are active.
     */
    public boolean isEmpty() {
        return isNullOrEmpty(audience) &&
                isNullOrEmpty(engagement) &&
                isNullOrEmpty(competitiveness) &&
                isNullOrEmpty(activity) &&
                isNullOrEmpty(platform) &&
                isNullOrEmpty(genres);
    }

    private static boolean isNullOrEmpty(Set<?> set) {
        return set == null || set.isEmpty();
    }

    /**
     * Returns the number of active filter categories.
     */
    public int activeFilterCount() {
        int count = 0;
        if (!isNullOrEmpty(audience))
            count++;
        if (!isNullOrEmpty(engagement))
            count++;
        if (!isNullOrEmpty(competitiveness))
            count++;
        if (!isNullOrEmpty(activity))
            count++;
        if (!isNullOrEmpty(platform))
            count++;
        if (!isNullOrEmpty(genres))
            count++;
        return count;
    }
}
