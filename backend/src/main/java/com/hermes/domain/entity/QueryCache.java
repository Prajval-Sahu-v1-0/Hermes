package com.hermes.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent L2 cache for query results.
 * Stores LLM-generated query lists to avoid repeat API calls.
 */
@Entity
@Table(name = "query_cache", indexes = {
        @Index(name = "idx_query_cache_expires", columnList = "expires_at")
})
public class QueryCache {

    @Id
    @Column(name = "digest_key", length = 64)
    private String digestKey;

    @Column(name = "normalized_query", nullable = false, columnDefinition = "TEXT")
    private String normalizedQuery;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "token_cost", nullable = false)
    private Integer tokenCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.hitCount == null) {
            this.hitCount = 0;
        }
    }

    // Getters and Setters
    public String getDigestKey() {
        return digestKey;
    }

    public void setDigestKey(String digestKey) {
        this.digestKey = digestKey;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public void setNormalizedQuery(String normalizedQuery) {
        this.normalizedQuery = normalizedQuery;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public Integer getTokenCost() {
        return tokenCost;
    }

    public void setTokenCost(Integer tokenCost) {
        this.tokenCost = tokenCost;
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

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
