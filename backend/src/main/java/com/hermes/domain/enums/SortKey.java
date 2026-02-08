package com.hermes.domain.enums;

/**
 * Whitelisted sort keys for session result ordering.
 * 
 * Each key maps to exactly ONE database column.
 * No dynamic or arbitrary sorting is allowed.
 * 
 * INVARIANTS:
 * - Sorting operates on precomputed, stored values ONLY
 * - No API calls, no ranking recomputation
 * - DB/cache read-only operations
 */
public enum SortKey {

    /**
     * Sort by final composite score (default ranking).
     * Column: score
     */
    FINAL_SCORE("score", "Final Score"),

    /**
     * Sort by genre relevance score.
     * Column: genre_relevance
     */
    RELEVANCE("genre_relevance", "Relevance"),

    /**
     * Sort by raw subscriber count (highest first).
     * Column: subscriber_count
     * 
     * NOTE: This sorts by the raw subscriber number, NOT by the
     * audience_fit score which measures preference matching.
     */
    SUBSCRIBERS("subscriber_count", "Subscribers"),

    /**
     * Sort by engagement quality score.
     * Column: engagement_quality
     */
    ENGAGEMENT("engagement_quality", "Engagement"),

    /**
     * Sort by recency of last video upload (most recently active first).
     * Column: last_video_date
     * 
     * NOTE: This sorts by the actual date of the most recent video,
     * NOT by the activity_consistency score (upload frequency).
     * Use this to find creators who have uploaded content recently.
     */
    ACTIVITY("last_video_date", "Recently Active"),

    /**
     * Sort by competitiveness score (derived metric).
     * Column: competitiveness_score
     * Formula: 0.40*audience + 0.35*engagement + 0.25*growth
     */
    COMPETITIVENESS("competitiveness_score", "Competitiveness");

    private final String columnName;
    private final String displayName;

    SortKey(String columnName, String displayName) {
        this.columnName = columnName;
        this.displayName = displayName;
    }

    /**
     * Returns the database column name for this sort key.
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Returns a human-readable display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a string to SortKey, defaulting to FINAL_SCORE for invalid input.
     */
    public static SortKey fromString(String value) {
        if (value == null || value.isBlank()) {
            return FINAL_SCORE;
        }
        try {
            return SortKey.valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return FINAL_SCORE;
        }
    }

    /**
     * Validates that a sort key string is whitelisted.
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            SortKey.valueOf(value.toUpperCase().replace("-", "_"));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
