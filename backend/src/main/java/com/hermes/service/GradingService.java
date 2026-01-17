package com.hermes.service;

import com.hermes.domain.entity.Creator;
import com.hermes.domain.grading.*;
import com.hermes.grading.scorer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the grading process for discovered creators.
 * 
 * Responsibilities:
 * 1. Enriches creators with YouTube statistics
 * 2. Runs all scorers to compute sub-scores
 * 3. Aggregates weighted final score
 * 4. Generates qualitative labels
 * 
 * No database writes - purely in-memory computation.
 */
@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final YouTubeEnrichmentService enrichmentService;

    public GradingService(YouTubeEnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    /**
     * Grades a list of creators based on user criteria.
     * 
     * @param creators List of creators to grade
     * @param criteria User's grading criteria (filters)
     * @return List of graded creators with scores and labels
     */
    public List<GradedCreator> gradeCreators(List<Creator> creators, GradingCriteria criteria) {
        if (creators == null || creators.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("[Grading] Grading {} creators for genre: {}", creators.size(), criteria.baseGenre());

        // Step 1: Enrich with YouTube statistics
        List<String> channelIds = creators.stream()
                .map(Creator::getChannelId)
                .collect(Collectors.toList());

        Map<String, ChannelStatistics> stats = enrichmentService.enrichChannels(channelIds);

        // Step 2: Grade each creator
        List<GradedCreator> gradedCreators = new ArrayList<>();
        for (Creator creator : creators) {
            ChannelStatistics channelStats = stats.get(creator.getChannelId());

            if (channelStats == null) {
                // Skip creators we couldn't enrich
                log.debug("[Grading] Skipping creator without stats: {}", creator.getChannelId());
                continue;
            }

            GradedCreator graded = gradeCreator(creator, channelStats, criteria);
            gradedCreators.add(graded);
        }

        log.info("[Grading] Graded {} creators", gradedCreators.size());
        return gradedCreators;
    }

    /**
     * Grades a single creator with enriched statistics.
     */
    private GradedCreator gradeCreator(
            Creator creator,
            ChannelStatistics stats,
            GradingCriteria criteria) {

        // Run all scorers
        double genreScore = GenreRelevanceScorer.score(
                creator.getChannelName(),
                creator.getDescription(),
                criteria.baseGenre());

        double audienceScore = AudienceFitScorer.score(
                stats.subscriberCount(),
                criteria.audienceScale());

        double engagementScore = EngagementScorer.score(
                stats.viewCount(),
                stats.subscriberCount(),
                criteria.engagementQuality());

        double activityScore = ActivityScorer.score(
                stats.videoCount(),
                stats.publishedAt(),
                criteria.activityLevel());

        double freshnessScore = FreshnessScorer.score(
                creator.getLastSeenAt());

        // Aggregate into final score
        CreatorScore score = CreatorScore.compute(
                genreScore,
                audienceScore,
                engagementScore,
                activityScore,
                freshnessScore);

        // Generate labels
        List<String> labels = LabelGenerator.generateLabels(score);

        return new GradedCreator(
                creator.getChannelId(),
                creator.getChannelName(),
                creator.getDescription(),
                creator.getProfileImageUrl(),
                creator.getPlatform(),
                score,
                labels);
    }

    /**
     * Creates default grading criteria from a genre.
     */
    public GradingCriteria createDefaultCriteria(String genre) {
        return GradingCriteria.withDefaults(genre);
    }

    /**
     * Creates grading criteria from user filters.
     */
    public GradingCriteria createCriteria(
            String genre,
            Map<String, String> filters) {

        GradingCriteria.AudienceScale audienceScale = parseAudienceScale(
                filters.getOrDefault("audience", "medium"));

        GradingCriteria.EngagementQuality engagementQuality = parseEngagementQuality(
                filters.getOrDefault("engagement", "medium"));

        GradingCriteria.ActivityLevel activityLevel = parseActivityLevel(
                filters.getOrDefault("activity", "consistent"));

        String location = filters.get("location");

        return new GradingCriteria(
                genre,
                audienceScale,
                engagementQuality,
                activityLevel,
                location);
    }

    private GradingCriteria.AudienceScale parseAudienceScale(String value) {
        return switch (value.toLowerCase()) {
            case "small" -> GradingCriteria.AudienceScale.SMALL;
            case "large" -> GradingCriteria.AudienceScale.LARGE;
            default -> GradingCriteria.AudienceScale.MEDIUM;
        };
    }

    private GradingCriteria.EngagementQuality parseEngagementQuality(String value) {
        return switch (value.toLowerCase()) {
            case "low" -> GradingCriteria.EngagementQuality.LOW;
            case "high" -> GradingCriteria.EngagementQuality.HIGH;
            default -> GradingCriteria.EngagementQuality.MEDIUM;
        };
    }

    private GradingCriteria.ActivityLevel parseActivityLevel(String value) {
        return switch (value.toLowerCase()) {
            case "occasional" -> GradingCriteria.ActivityLevel.OCCASIONAL;
            case "very_active", "veryactive", "very active" -> GradingCriteria.ActivityLevel.VERY_ACTIVE;
            default -> GradingCriteria.ActivityLevel.CONSISTENT;
        };
    }
}
