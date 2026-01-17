package com.hermes.grading.scorer;

import com.hermes.domain.grading.GradingCriteria.ActivityLevel;
import java.time.Instant;
import java.time.Duration;

/**
 * Scores activity consistency based on upload frequency.
 * Calculated as videos per month since channel creation.
 * 
 * Pure function: no DB access, no side effects, deterministic.
 */
public final class ActivityScorer {

    private ActivityScorer() {
    }

    /**
     * Computes activity score based on upload frequency.
     * 
     * @param videoCount         Total videos on channel
     * @param channelPublishedAt When the channel was created
     * @param preferredLevel     User's preferred activity level (optional)
     * @return Score in range [0.0, 1.0]
     */
    public static double score(long videoCount, Instant channelPublishedAt, ActivityLevel preferredLevel) {
        double uploadsPerMonth = calculateUploadsPerMonth(videoCount, channelPublishedAt);

        // Normalize uploads per month to [0, 1]
        double normalizedScore = normalizeUploads(uploadsPerMonth);

        // If user has a preference, adjust based on match
        if (preferredLevel != null) {
            double preferenceMatch = matchPreference(uploadsPerMonth, preferredLevel);
            normalizedScore = (normalizedScore + preferenceMatch) / 2.0;
        }

        return Math.min(1.0, Math.max(0.0, normalizedScore));
    }

    /**
     * Calculates uploads per month from video count and channel age.
     */
    private static double calculateUploadsPerMonth(long videoCount, Instant publishedAt) {
        if (publishedAt == null || videoCount == 0) {
            return 0.0;
        }

        long daysSinceCreation = Duration.between(publishedAt, Instant.now()).toDays();
        if (daysSinceCreation < 30) {
            return videoCount; // Treat new channels as one month
        }

        double months = daysSinceCreation / 30.0;
        return videoCount / months;
    }

    /**
     * Normalizes uploads per month to [0, 1].
     * Uses diminishing returns above 8 uploads/month.
     */
    private static double normalizeUploads(double uploadsPerMonth) {
        if (uploadsPerMonth <= 0)
            return 0.0;
        if (uploadsPerMonth <= 1)
            return uploadsPerMonth * 0.3;
        if (uploadsPerMonth <= 4)
            return 0.3 + (uploadsPerMonth - 1) / 3.0 * 0.4;
        if (uploadsPerMonth <= 8)
            return 0.7 + (uploadsPerMonth - 4) / 4.0 * 0.2;
        return 0.9 + Math.min(0.1, (uploadsPerMonth - 8) / 20.0 * 0.1);
    }

    /**
     * Calculates how well the upload frequency matches user preference.
     */
    private static double matchPreference(double uploadsPerMonth, ActivityLevel preference) {
        double min = preference.getMinUploadsPerMonth();
        double max = preference.getMaxUploadsPerMonth();

        if (uploadsPerMonth >= min && uploadsPerMonth < max) {
            return 1.0; // Perfect match
        }

        // Calculate distance-based penalty
        if (uploadsPerMonth < min) {
            double distance = (min - uploadsPerMonth) / min;
            return Math.max(0.0, 1.0 - distance) * 0.7;
        } else {
            // Above max (very active when user wants less)
            return 0.8; // Slight penalty for being too active
        }
    }
}
