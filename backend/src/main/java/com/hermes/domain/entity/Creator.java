package com.hermes.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a discovered creator profile.
 * Stores raw, ungraded creator data from platform searches.
 * Designed for multi-platform extensibility with deduplication support.
 */
@Entity
@Table(name = "creators", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "platform", "channel_id" })
}, indexes = {
        @Index(name = "idx_platform", columnList = "platform"),
        @Index(name = "idx_base_genre", columnList = "base_genre"),
        @Index(name = "idx_discovered_at", columnList = "discovered_at"),
        @Index(name = "idx_status", columnList = "status")
})
public class Creator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identity
    @Column(nullable = false)
    private String platform;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(length = 2000)
    private String description;

    // Visual identity
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // Discovery context
    @Column(name = "base_genre", nullable = false)
    private String baseGenre;

    @Column(name = "origin_query")
    private String originQuery;

    // Light metadata
    private String language;
    private String country;

    @Column(name = "content_category")
    private String contentCategory;

    // Embedding fields for vector scoring (one-time LLM ingestion)
    @Column(name = "profile_embedding", columnDefinition = "FLOAT8[]")
    private double[] profileEmbedding;

    @Column(name = "embedding_model", length = 50)
    private String embeddingModel;

    @Column(name = "embedding_created_at")
    private Instant embeddingCreatedAt;

    @Column(name = "compressed_bio", length = 500)
    private String compressedBio;

    @Column(name = "content_tags", columnDefinition = "VARCHAR(50)[]")
    private String[] contentTags;

    @Column(name = "ingestion_status", length = 20)
    private String ingestionStatus = "pending";

    // Lifecycle
    @Column(name = "discovered_at", nullable = false, updatable = false)
    private Instant discoveredAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.discoveredAt = now;
        this.lastSeenAt = now;
        if (status == null)
            this.status = Status.ACTIVE;
        if (source == null)
            this.source = Source.API;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastSeenAt = Instant.now();
    }

    // Enums
    public enum Status {
        ACTIVE,
        INACTIVE,
        HIDDEN
    }

    public enum Source {
        API,
        MANUAL,
        IMPORTED
    }

    // Default constructor required by JPA
    public Creator() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
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

    public String getBaseGenre() {
        return baseGenre;
    }

    public void setBaseGenre(String baseGenre) {
        this.baseGenre = baseGenre;
    }

    public String getOriginQuery() {
        return originQuery;
    }

    public void setOriginQuery(String originQuery) {
        this.originQuery = originQuery;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getContentCategory() {
        return contentCategory;
    }

    public void setContentCategory(String contentCategory) {
        this.contentCategory = contentCategory;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(Instant discoveredAt) {
        this.discoveredAt = discoveredAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    // Embedding getters and setters
    public double[] getProfileEmbedding() {
        return profileEmbedding;
    }

    public void setProfileEmbedding(double[] profileEmbedding) {
        this.profileEmbedding = profileEmbedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Instant getEmbeddingCreatedAt() {
        return embeddingCreatedAt;
    }

    public void setEmbeddingCreatedAt(Instant embeddingCreatedAt) {
        this.embeddingCreatedAt = embeddingCreatedAt;
    }

    public String getCompressedBio() {
        return compressedBio;
    }

    public void setCompressedBio(String compressedBio) {
        this.compressedBio = compressedBio;
    }

    public String[] getContentTags() {
        return contentTags;
    }

    public void setContentTags(String[] contentTags) {
        this.contentTags = contentTags;
    }

    public String getIngestionStatus() {
        return ingestionStatus;
    }

    public void setIngestionStatus(String ingestionStatus) {
        this.ingestionStatus = ingestionStatus;
    }
}
