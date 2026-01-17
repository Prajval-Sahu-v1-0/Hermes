package com.hermes.service;

import com.hermes.domain.model.CreatorProfile;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class ScoringEngine {

    /**
     * Scores a creator based on user filters.
     * Score is normalized between 0.0 and 100.0.
     */
    public double calculateScore(CreatorProfile profile, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            profile.qualitativeLabels().put("status", "Analyzed");
            return 50.0;
        }

        double score = 50.0;
        Map<String, String> labels = profile.qualitativeLabels();

        // 1. Audience Scale (Subscribers)
        String audiencePref = filters.getOrDefault("audience", "medium");
        score += evaluateAudience(profile.subscriberCount(), audiencePref, labels);

        // 2. Performance (View/Sub Ratio or consistent views)
        String performancePref = filters.getOrDefault("engagement", "steady");
        score += evaluatePerformance(profile.viewCount(), profile.subscriberCount(), performancePref, labels);

        // 3. Consistency (Videos)
        score += evaluateConsistency(profile.videoCount(), labels);

        return Math.clamp(score, 0.0, 100.0);
    }

    private double evaluateAudience(long count, String pref, Map<String, String> labels) {
        String label = (count > 1000000) ? "Mega" : (count > 100000) ? "Mid-Tier" : "Rising Star";
        labels.put("audience_scale", label);

        return switch (pref.toLowerCase()) {
            case "nano" -> (count < 10000) ? 10 : -5;
            case "micro" -> (count >= 10000 && count < 50000) ? 10 : -5;
            case "mid" -> (count >= 50000 && count < 200000) ? 10 : -5;
            case "macro" -> (count >= 200000) ? 10 : -5;
            default -> 0;
        };
    }

    private double evaluatePerformance(long views, long subs, String pref, Map<String, String> labels) {
        double ratio = (subs > 0) ? (double) views / subs : 0;
        String label = (ratio > 50) ? "Viral Potential" : (ratio > 10) ? "High Engagement" : "Consistent";
        labels.put("engagement_quality", label);

        return switch (pref.toLowerCase()) {
            case "high" -> (ratio > 20) ? 15 : -5;
            case "steady" -> (ratio >= 5 && ratio <= 20) ? 10 : 0;
            default -> 0;
        };
    }

    private double evaluateConsistency(long videoCount, Map<String, String> labels) {
        String label = (videoCount > 500) ? "Veteran" : (videoCount > 100) ? "Active" : "Developing";
        labels.put("activity_level", label);
        return (videoCount > 100) ? 5 : 0;
    }
}
