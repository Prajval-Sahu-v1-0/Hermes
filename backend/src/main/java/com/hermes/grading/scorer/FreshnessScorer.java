package com.hermes.grading.scorer;

import java.time.Instant;
import java.time.Duration;

/**
 * Scores channel freshness based on last activity.
 * Penalizes stale or inactive channels.
 * 
 * Pure function: no DB access, no side effects, deterministic.
 */
public final class FreshnessScorer {

    // Freshness decay thresholds
    private static final long DAYS_FRESH = 7; // Full score within 7 days
    private static final long DAYS_RECENT = 30; // High score within 30 days
    private static final long DAYS_STALE = 90; // Moderate score within 90 days
    private static final long DAYS_OLD = 180; // Low score within 180 days

    private FreshnessScorer() {
    }

    /**
     * Computes freshness score based on last seen/activity time.
     * 
     * @param lastSeenAt When the channel was last seen/updated
     * @return Score in range [0.0, 1.0]
     */
    public static double score(Instant lastSeenAt) {
        if (lastSeenAt == null) {
            return 0.5; // Neutral score if unknown
        }

        long daysSinceLastSeen = Duration.between(lastSeenAt, Instant.now()).toDays();

        if (daysSinceLastSeen < 0) {
            return 1.0; // Future date (shouldn't happen, but handle gracefully)
        }

        if (daysSinceLastSeen <= DAYS_FRESH) {
            return 1.0;
        }

        if (daysSinceLastSeen <= DAYS_RECENT) {
            // Linear decay from 1.0 to 0.8
            double progress = (double) (daysSinceLastSeen - DAYS_FRESH) / (DAYS_RECENT - DAYS_FRESH);
            return 1.0 - (progress * 0.2);
        }

        if (daysSinceLastSeen <= DAYS_STALE) {
            // Linear decay from 0.8 to 0.5
            double progress = (double) (daysSinceLastSeen - DAYS_RECENT) / (DAYS_STALE - DAYS_RECENT);
            return 0.8 - (progress * 0.3);
        }

        if (daysSinceLastSeen <= DAYS_OLD) {
            // Linear decay from 0.5 to 0.2
            double progress = (double) (daysSinceLastSeen - DAYS_STALE) / (DAYS_OLD - DAYS_STALE);
            return 0.5 - (progress * 0.3);
        }

        // Very old channels get minimum score
        return 0.1;
    }
}
