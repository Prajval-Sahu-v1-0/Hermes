package com.hermes.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Query embedding entity for vector similarity search.
 * Stores one embedding per normalized query.
 */
@Entity
@Table(name = "query_embeddings")
public class QueryEmbedding {

    @Id
    @Column(name = "digest_key", length = 64)
    private String digestKey;

    @Column(name = "normalized_query", nullable = false, columnDefinition = "TEXT")
    private String normalizedQuery;

    @Column(name = "embedding", columnDefinition = "FLOAT8[]")
    private double[] embedding;

    @Column(name = "model_version", length = 50, nullable = false)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
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

    public double[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
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

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
