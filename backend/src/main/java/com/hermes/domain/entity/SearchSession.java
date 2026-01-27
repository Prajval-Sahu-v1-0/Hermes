package com.hermes.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a materialized search session.
 * Each unique query creates exactly one session.
 * Pagination operates on stored results without API calls.
 */
@Entity
@Table(name = "search_session", uniqueConstraints = @UniqueConstraint(columnNames = { "query_digest",
        "platform" }), indexes = {
                @Index(name = "idx_session_digest", columnList = "query_digest, platform"),
                @Index(name = "idx_session_expires", columnList = "expires_at")
        })
public class SearchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "query_digest", nullable = false, length = 64)
    private String queryDigest;

    @Column(name = "normalized_query", nullable = false, length = 500)
    private String normalizedQuery;

    @Column(nullable = false, length = 20)
    private String platform = "youtube";

    @Column(name = "total_results", nullable = false)
    private int totalResults = 0;

    @Column(name = "youtube_quota_used", nullable = false)
    private int youtubeQuotaUsed = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
    }

    // Constructors
    public SearchSession() {
    }

    public SearchSession(String queryDigest, String normalizedQuery, String platform,
            int totalResults, int youtubeQuotaUsed, Instant expiresAt) {
        this.queryDigest = queryDigest;
        this.normalizedQuery = normalizedQuery;
        this.platform = platform;
        this.totalResults = totalResults;
        this.youtubeQuotaUsed = youtubeQuotaUsed;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getQueryDigest() {
        return queryDigest;
    }

    public void setQueryDigest(String queryDigest) {
        this.queryDigest = queryDigest;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
    }

    public int getYoutubeQuotaUsed() {
        return youtubeQuotaUsed;
    }

    public void setYoutubeQuotaUsed(int youtubeQuotaUsed) {
        this.youtubeQuotaUsed = youtubeQuotaUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void touch() {
        this.lastAccessedAt = Instant.now();
    }
}
