package com.hermes.ingestion;

import com.hermes.domain.entity.Creator;
import com.hermes.domain.model.CreatorProfile;
import com.hermes.governor.TokenGovernor;
import com.hermes.repository.CreatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One-time creator ingestion pipeline.
 * Processes new creators with LLM embedding generation.
 * Ensures each creator is only processed once.
 */
@Service
public class CreatorIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CreatorIngestionService.class);
    private static final String EMBEDDING_MODEL = "embed-english-v3.0";

    private final CreatorRepository creatorRepository;
    private final EmbeddingService embeddingService;
    private final TokenGovernor tokenGovernor;

    public CreatorIngestionService(
            CreatorRepository creatorRepository,
            EmbeddingService embeddingService,
            TokenGovernor tokenGovernor) {
        this.creatorRepository = creatorRepository;
        this.embeddingService = embeddingService;
        this.tokenGovernor = tokenGovernor;
    }

    /**
     * Ingests a new creator profile with embedding generation.
     * Only processes if ingestion_status is 'pending' or embedding is null.
     * 
     * @param profile     CreatorProfile from YouTube API
     * @param baseGenre   The search genre
     * @param originQuery The query that discovered this creator
     * @return true if ingestion was performed
     */
    @Transactional
    public boolean ingestCreator(CreatorProfile profile, String baseGenre, String originQuery) {
        Optional<Creator> existing = creatorRepository.findByPlatformAndChannelId("youtube", profile.id());

        if (existing.isPresent()) {
            Creator creator = existing.get();
            // Check if needs re-ingestion
            if (creator.getProfileEmbedding() != null &&
                    !"pending".equals(creator.getIngestionStatus())) {
                log.debug("[Ingestion] Skipping already ingested creator: {}", profile.id());
                // Update lastSeenAt only
                creator.setLastSeenAt(Instant.now());
                creatorRepository.save(creator);
                return false;
            }
            return processCreator(creator, profile);
        } else {
            // Create new creator
            Creator creator = new Creator();
            creator.setPlatform("youtube");
            creator.setChannelId(profile.id());
            creator.setChannelName(profile.displayName());
            creator.setBaseGenre(baseGenre);
            creator.setOriginQuery(originQuery);
            creator.setDescription(truncate(profile.bio(), 2000));
            creator.setProfileImageUrl(profile.profileImageUrl());
            creator.setCountry(profile.location());
            creator.setIngestionStatus("pending");

            creatorRepository.save(creator);
            return processCreator(creator, profile);
        }
    }

    /**
     * Processes a creator: generates compressed bio and embedding.
     */
    private boolean processCreator(Creator creator, CreatorProfile profile) {
        // Check budget before LLM call
        var decision = tokenGovernor.checkBudget(500); // Estimate for embedding
        if (!decision.canUseLLM()) {
            log.warn("[Ingestion] Budget exceeded, deferring ingestion for: {}", creator.getChannelId());
            creator.setIngestionStatus("deferred");
            creatorRepository.save(creator);
            return false;
        }

        try {
            // 1. Compress bio to embedding text
            String embeddingText = buildEmbeddingText(creator, profile);

            // 2. Generate embedding
            double[] embedding = embeddingService.embed(embeddingText, EmbeddingService.InputType.SEARCH_DOCUMENT);

            // 3. Extract content tags (deterministic, no LLM)
            String[] contentTags = extractContentTags(profile);

            // 4. Update creator
            creator.setProfileEmbedding(embedding);
            creator.setEmbeddingModel(EMBEDDING_MODEL);
            creator.setEmbeddingCreatedAt(Instant.now());
            creator.setCompressedBio(truncate(embeddingText, 500));
            creator.setContentTags(contentTags);
            creator.setIngestionStatus("complete");

            creatorRepository.save(creator);
            log.info("[Ingestion] Successfully ingested creator: {}", creator.getChannelName());
            return true;

        } catch (Exception e) {
            log.error("[Ingestion] Failed to ingest creator {}: {}", creator.getChannelId(), e.getMessage());
            creator.setIngestionStatus("failed");
            creatorRepository.save(creator);
            return false;
        }
    }

    /**
     * Builds the text to be embedded for semantic search.
     */
    private String buildEmbeddingText(Creator creator, CreatorProfile profile) {
        StringBuilder sb = new StringBuilder();

        // Channel name (weighted by repetition)
        sb.append(creator.getChannelName()).append(". ");

        // Description (truncated)
        String bio = profile.bio() != null ? profile.bio() : "";
        sb.append(truncate(bio, 300)).append(" ");

        // Metrics context
        if (profile.subscriberCount() > 1000000) {
            sb.append("Major creator. ");
        } else if (profile.subscriberCount() > 100000) {
            sb.append("Established creator. ");
        }

        // Country
        if (profile.location() != null) {
            sb.append("Based in ").append(profile.location()).append(". ");
        }

        return sb.toString().trim();
    }

    /**
     * Extracts content tags deterministically from profile.
     * No LLM used - pattern matching only.
     */
    private String[] extractContentTags(CreatorProfile profile) {
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        String text = ((profile.displayName() != null ? profile.displayName() : "") + " " +
                (profile.bio() != null ? profile.bio() : "")).toLowerCase();

        // Category patterns
        if (text.contains("gaming") || text.contains("gamer") || text.contains("gameplay")) {
            tags.add("gaming");
        }
        if (text.contains("music") || text.contains("song") || text.contains("singer")) {
            tags.add("music");
        }
        if (text.contains("comedy") || text.contains("funny") || text.contains("humor")) {
            tags.add("comedy");
        }
        if (text.contains("tech") || text.contains("technology") || text.contains("review")) {
            tags.add("tech");
        }
        if (text.contains("vlog") || text.contains("daily") || text.contains("lifestyle")) {
            tags.add("lifestyle");
        }
        if (text.contains("education") || text.contains("learn") || text.contains("tutorial")) {
            tags.add("education");
        }
        if (text.contains("fitness") || text.contains("workout") || text.contains("gym")) {
            tags.add("fitness");
        }
        if (text.contains("cooking") || text.contains("recipe") || text.contains("food")) {
            tags.add("food");
        }
        if (text.contains("beauty") || text.contains("makeup") || text.contains("fashion")) {
            tags.add("beauty");
        }
        if (text.contains("news") || text.contains("politics") || text.contains("commentary")) {
            tags.add("commentary");
        }

        // Limit to 5 tags
        return tags.stream().limit(5).toArray(String[]::new);
    }

    /**
     * Batch ingests multiple creators asynchronously.
     */
    @Async
    public void ingestBatch(List<CreatorProfile> profiles, String baseGenre, String originQuery) {
        log.info("[Ingestion] Starting batch ingestion of {} creators", profiles.size());
        int ingested = 0;
        for (CreatorProfile profile : profiles) {
            if (ingestCreator(profile, baseGenre, originQuery)) {
                ingested++;
            }
        }
        log.info("[Ingestion] Batch complete: {}/{} creators ingested", ingested, profiles.size());
    }

    /**
     * Re-processes creators with failed or pending status.
     */
    @Transactional
    public int reprocessPending() {
        List<Creator> pending = creatorRepository.findByIngestionStatus("pending");
        pending.addAll(creatorRepository.findByIngestionStatus("deferred"));

        int reprocessed = 0;
        for (Creator creator : pending) {
            CreatorProfile profile = creatorToProfile(creator);
            if (processCreator(creator, profile)) {
                reprocessed++;
            }
        }
        log.info("[Ingestion] Reprocessed {}/{} pending creators", reprocessed, pending.size());
        return reprocessed;
    }

    private CreatorProfile creatorToProfile(Creator creator) {
        return new CreatorProfile(
                creator.getChannelId(),
                null,
                creator.getChannelName(),
                creator.getDescription(),
                creator.getProfileImageUrl(),
                0, 0, 0,
                List.of(),
                creator.getCountry(),
                new java.util.HashMap<>(),
                0.0);
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
