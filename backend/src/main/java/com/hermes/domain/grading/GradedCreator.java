package com.hermes.domain.grading;

import java.time.Instant;
import java.util.List;

/**
 * A graded creator with identity, score, and qualitative labels.
 * This is the output of the grading process.
 */
public record GradedCreator(
        String channelId,
        String channelName,
        String description,
        String profileImageUrl,
        String platform,
        CreatorScore score,
        List<String> labels,
        long subscriberCount,
        long viewCount,
        Instant lastVideoDate) { // Most recent video upload timestamp for "Recently Active" sorting
    /**
     * Returns the final score for sorting/ranking.
     */
    public double getFinalScore() {
        return score != null ? score.finalScore() : 0.0;
    }
}
