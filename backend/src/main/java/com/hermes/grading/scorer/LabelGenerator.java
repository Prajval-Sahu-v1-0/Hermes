package com.hermes.grading.scorer;

import com.hermes.domain.grading.CreatorScore;
import com.hermes.domain.grading.GradedCreator;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates qualitative labels from creator scores.
 * Labels are human-readable explanations of why a creator scored well/poorly.
 * 
 * Pure function: no side effects, deterministic.
 */
public final class LabelGenerator {

    private static final double HIGH_THRESHOLD = 0.75;
    private static final double MEDIUM_THRESHOLD = 0.5;
    private static final double LOW_THRESHOLD = 0.25;

    private LabelGenerator() {
    }

    /**
     * Generates labels based on score breakdown.
     * 
     * @param score The creator's score breakdown
     * @return List of qualitative labels
     */
    public static List<String> generateLabels(CreatorScore score) {
        List<String> labels = new ArrayList<>();

        // Genre relevance labels
        if (score.genreRelevance() >= HIGH_THRESHOLD) {
            labels.add("Strong genre fit");
        } else if (score.genreRelevance() >= MEDIUM_THRESHOLD) {
            labels.add("Good genre match");
        }

        // Audience fit labels
        if (score.audienceFit() >= HIGH_THRESHOLD) {
            labels.add("Perfect audience size");
        } else if (score.audienceFit() >= MEDIUM_THRESHOLD) {
            labels.add("Suitable audience");
        }

        // Engagement labels
        if (score.engagementQuality() >= HIGH_THRESHOLD) {
            labels.add("High engagement");
        } else if (score.engagementQuality() >= MEDIUM_THRESHOLD) {
            labels.add("Good engagement");
        } else if (score.engagementQuality() < LOW_THRESHOLD) {
            labels.add("Low engagement");
        }

        // Activity labels
        if (score.activityConsistency() >= HIGH_THRESHOLD) {
            labels.add("Very active");
        } else if (score.activityConsistency() >= MEDIUM_THRESHOLD) {
            labels.add("Consistently active");
        } else if (score.activityConsistency() < LOW_THRESHOLD) {
            labels.add("Occasionally active");
        }

        // Freshness labels
        if (score.freshness() >= HIGH_THRESHOLD) {
            labels.add("Recently active");
        } else if (score.freshness() < LOW_THRESHOLD) {
            labels.add("Inactive recently");
        }

        // Overall score labels
        if (score.finalScore() >= 0.8) {
            labels.add("Top match");
        } else if (score.finalScore() >= 0.6) {
            labels.add("Good match");
        }

        return labels;
    }
}
