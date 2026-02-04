package com.hermes.domain.grading;

import java.time.Instant;

/**
 * Per-video statistics from YouTube Data API v3.
 * Used for behavior-based engagement scoring.
 * 
 * Fields correspond to YouTube API video statistics:
 * - viewCount: Total video views
 * - likeCount: Total likes on the video
 * - commentCount: Total comments on the video
 * - publishedAt: Video publication timestamp (for recency weighting)
 */
public record VideoStatistics(
        String videoId,
        long viewCount,
        long likeCount,
        long commentCount,
        Instant publishedAt) {

    /**
     * Calculates the video interaction score.
     * 
     * Formula: likeCount * 1.0 + commentCount * 2.0
     * 
     * Comments are weighted higher than likes because:
     * - Comments require more effort than a simple like
     * - Comments indicate deeper audience investment
     * - Comments drive algorithmic visibility via engagement signals
     * 
     * @return Interaction score (sum of weighted interactions)
     */
    public double interactionScore() {
        return (likeCount * 1.0) + (commentCount * 2.0);
    }

    /**
     * Calculates engagement rate for this video.
     * 
     * Formula: interactionScore / viewCount
     * 
     * This normalizes engagement by views, allowing fair comparison
     * between high-view and low-view videos.
     * 
     * @param minViewThreshold Minimum views required to calculate rate
     * @return Engagement rate in range [0, ~0.2 typically], or 0 if below threshold
     */
    public double engagementRate(int minViewThreshold) {
        if (viewCount < minViewThreshold) {
            return 0.0; // Skip low-view videos to avoid noise
        }
        return interactionScore() / viewCount;
    }
}
