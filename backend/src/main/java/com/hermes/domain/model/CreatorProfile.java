package com.hermes.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CreatorProfile(
        String id,
        String username, // Channel handle
        String displayName, // Channel title
        String bio,
        String profileImageUrl,
        long subscriberCount,
        long videoCount,
        long viewCount,
        List<String> categories,
        String location,
        Map<String, String> qualitativeLabels, // e.g., {"audience": "High Growth", "engagement": "Elite"}
        double calculatedScore,
        Instant lastVideoDate) { // Most recent video upload timestamp
    public CreatorProfile withScore(double score) {
        return new CreatorProfile(
                id, username, displayName, bio, profileImageUrl,
                subscriberCount, videoCount, viewCount, categories, location,
                qualitativeLabels, score, lastVideoDate);
    }
}
