package com.hermes.repository;

import com.hermes.domain.entity.Creator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for Creator entities.
 * Provides deduplication lookup and basic CRUD operations.
 */
@Repository
public interface CreatorRepository extends JpaRepository<Creator, Long> {

    /**
     * Find a creator by platform and channel ID.
     * Used for deduplication during search result persistence.
     *
     * @param platform  the platform identifier (e.g., "youtube")
     * @param channelId the platform-specific channel identifier
     * @return the creator if found, empty otherwise
     */
    Optional<Creator> findByPlatformAndChannelId(String platform, String channelId);
}
