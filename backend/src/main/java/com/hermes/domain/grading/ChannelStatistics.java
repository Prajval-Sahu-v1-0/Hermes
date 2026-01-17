package com.hermes.domain.grading;

import java.time.Instant;

/**
 * In-memory YouTube channel statistics for grading.
 * Not persisted to database - fetched on demand.
 */
public record ChannelStatistics(
        String channelId,
        long subscriberCount,
        long viewCount,
        long videoCount,
        Instant publishedAt) {
    /**
     * Calculates views per subscriber ratio.
     * Returns 0 if subscriber count is 0.
     */
    public double viewsPerSubscriber() {
        if (subscriberCount == 0)
            return 0.0;
        return (double) viewCount / subscriberCount;
    }

    /**
     * Calculates uploads per month since channel creation.
     * Returns 0 if channel was created less than a month ago.
     */
    public double uploadsPerMonth() {
        if (publishedAt == null)
            return 0.0;

        long daysSinceCreation = java.time.Duration.between(publishedAt, Instant.now()).toDays();
        if (daysSinceCreation < 30)
            return videoCount; // Treat as one month

        double months = daysSinceCreation / 30.0;
        return videoCount / months;
    }

    /**
     * Calculates channel age in months.
     */
    public double channelAgeMonths() {
        if (publishedAt == null)
            return 0.0;
        long days = java.time.Duration.between(publishedAt, Instant.now()).toDays();
        return days / 30.0;
    }
}
