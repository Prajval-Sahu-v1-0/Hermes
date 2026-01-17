package com.hermes.service;

import com.hermes.domain.entity.Creator;
import com.hermes.domain.model.CreatorProfile;
import com.hermes.repository.CreatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for persisting discovered creator profiles to the database.
 * Handles deduplication by (platform, channelId) and updates lastSeenAt
 * for existing creators.
 */
@Service
public class CreatorPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(CreatorPersistenceService.class);
    private static final String PLATFORM_YOUTUBE = "youtube";

    private final CreatorRepository creatorRepository;

    public CreatorPersistenceService(CreatorRepository creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    /**
     * Persists all discovered creators from a search result.
     * For each creator:
     * - If new: creates a new record
     * - If exists: updates lastSeenAt and originQuery
     *
     * @param queryResults map of query to discovered creator profiles
     * @param baseGenre    the original genre/style search term
     * @return the number of creators persisted (new + updated)
     */
    @Transactional
    public int persistDiscoveredCreators(Map<String, List<CreatorProfile>> queryResults, String baseGenre) {
        int persisted = 0;

        for (Map.Entry<String, List<CreatorProfile>> entry : queryResults.entrySet()) {
            String originQuery = entry.getKey();
            List<CreatorProfile> profiles = entry.getValue();

            for (CreatorProfile profile : profiles) {
                if (persistOrUpdateCreator(profile, baseGenre, originQuery)) {
                    persisted++;
                }
            }
        }

        log.info("[Persistence] Persisted/updated {} creators for genre: {}", persisted, baseGenre);
        return persisted;
    }

    /**
     * Persists a single creator profile or updates if already exists.
     * Handles concurrent duplicate inserts gracefully.
     *
     * @param profile     the creator profile from YouTube search
     * @param baseGenre   the original genre/style search term
     * @param originQuery the specific query that discovered this creator
     * @return true if persisted/updated successfully, false if skipped due to
     *         duplicate
     */
    @Transactional
    public boolean persistOrUpdateCreator(CreatorProfile profile, String baseGenre, String originQuery) {
        try {
            Optional<Creator> existing = creatorRepository.findByPlatformAndChannelId(
                    PLATFORM_YOUTUBE, profile.id());

            if (existing.isPresent()) {
                // Update existing creator
                Creator creator = existing.get();
                creator.setLastSeenAt(Instant.now());
                creator.setOriginQuery(originQuery);
                // Update profile image if changed
                if (profile.profileImageUrl() != null) {
                    creator.setProfileImageUrl(profile.profileImageUrl());
                }
                creatorRepository.save(creator);
                return true;
            } else {
                // Create new creator
                Creator creator = new Creator();
                creator.setPlatform(PLATFORM_YOUTUBE);
                creator.setChannelId(profile.id());
                creator.setChannelName(profile.displayName());
                creator.setDescription(truncateDescription(profile.bio()));
                creator.setProfileImageUrl(profile.profileImageUrl());
                creator.setBaseGenre(baseGenre);
                creator.setOriginQuery(originQuery);
                creator.setCountry(profile.location());

                // Set content category from first category if available
                if (profile.categories() != null && !profile.categories().isEmpty()) {
                    creator.setContentCategory(profile.categories().get(0));
                }

                creatorRepository.save(creator);
                return true;
            }
        } catch (DataIntegrityViolationException e) {
            // Duplicate key - the same channel appeared in multiple queries
            // Just skip it, this is expected behavior
            log.debug("[Persistence] Skipping duplicate creator: {}", profile.id());
            return false;
        }
    }

    /**
     * Truncates description to fit database column constraint.
     */
    private String truncateDescription(String description) {
        if (description == null) {
            return null;
        }
        return description.length() > 2000 ? description.substring(0, 2000) : description;
    }
}
