package com.hermes.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a single ranked result within a SearchSession.
 * Pagination retrieves slices from this table without API calls.
 */
@Entity
@Table(name = "search_session_result", indexes = @Index(name = "idx_session_result_lookup", columnList = "session_id, rank"))
@IdClass(SearchSessionResult.SearchSessionResultId.class)
public class SearchSessionResult {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "rank")
    private int rank;

    @Column(name = "channel_id", nullable = false, length = 50)
    private String channelId;

    @Column(name = "channel_name", length = 255)
    private String channelName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(nullable = false)
    private double score;

    @Column(name = "genre_relevance")
    private double genreRelevance;

    @Column(name = "audience_fit")
    private double audienceFit;

    @Column(name = "engagement_quality")
    private double engagementQuality;

    @Column(name = "activity_consistency")
    private double activityConsistency;

    @Column(name = "competitiveness_score")
    private double competitivenessScore;

    @Column(name = "freshness")
    private double freshness;

    @Column(name = "subscriber_count")
    private long subscriberCount;

    @Column(columnDefinition = "TEXT[]")
    private String[] labels;

    @Column(name = "last_video_date")
    private Instant lastVideoDate;

    // Constructors
    public SearchSessionResult() {
    }

    public SearchSessionResult(UUID sessionId, int rank, String channelId, String channelName,
            String description, String profileImageUrl, double score, String[] labels) {
        this.sessionId = sessionId;
        this.rank = rank;
        this.channelId = channelId;
        this.channelName = channelName;
        this.description = description;
        this.profileImageUrl = profileImageUrl;
        this.score = score;
        this.labels = labels;
    }

    // Getters and Setters
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getGenreRelevance() {
        return genreRelevance;
    }

    public void setGenreRelevance(double genreRelevance) {
        this.genreRelevance = genreRelevance;
    }

    public double getAudienceFit() {
        return audienceFit;
    }

    public void setAudienceFit(double audienceFit) {
        this.audienceFit = audienceFit;
    }

    public double getEngagementQuality() {
        return engagementQuality;
    }

    public void setEngagementQuality(double engagementQuality) {
        this.engagementQuality = engagementQuality;
    }

    public double getActivityConsistency() {
        return activityConsistency;
    }

    public void setActivityConsistency(double activityConsistency) {
        this.activityConsistency = activityConsistency;
    }

    public double getCompetitivenessScore() {
        return competitivenessScore;
    }

    public void setCompetitivenessScore(double competitivenessScore) {
        this.competitivenessScore = competitivenessScore;
    }

    public double getFreshness() {
        return freshness;
    }

    public void setFreshness(double freshness) {
        this.freshness = freshness;
    }

    public long getSubscriberCount() {
        return subscriberCount;
    }

    public void setSubscriberCount(long subscriberCount) {
        this.subscriberCount = subscriberCount;
    }

    public String[] getLabels() {
        return labels;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public Instant getLastVideoDate() {
        return lastVideoDate;
    }

    public void setLastVideoDate(Instant lastVideoDate) {
        this.lastVideoDate = lastVideoDate;
    }

    /**
     * Composite primary key class.
     */
    public static class SearchSessionResultId implements Serializable {
        private UUID sessionId;
        private int rank;

        public SearchSessionResultId() {
        }

        public SearchSessionResultId(UUID sessionId, int rank) {
            this.sessionId = sessionId;
            this.rank = rank;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof SearchSessionResultId that))
                return false;
            return rank == that.rank && Objects.equals(sessionId, that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId, rank);
        }
    }
}
