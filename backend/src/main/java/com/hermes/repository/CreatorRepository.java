package com.hermes.repository;

import com.hermes.domain.entity.Creator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Creator entities.
 * Provides deduplication lookup and ingestion status queries.
 */
@Repository
public interface CreatorRepository extends JpaRepository<Creator, Long> {

    /**
     * Find a creator by platform and channel ID.
     * Used for deduplication during search result persistence.
     */
    Optional<Creator> findByPlatformAndChannelId(String platform, String channelId);

    /**
     * Find creators by ingestion status.
     * Used for reprocessing pending/failed ingestions.
     */
    List<Creator> findByIngestionStatus(String ingestionStatus);

    /**
     * Find creators with embeddings for vector scoring.
     */
    @Query("SELECT c FROM Creator c WHERE c.profileEmbedding IS NOT NULL AND c.platform = :platform")
    List<Creator> findWithEmbeddings(@Param("platform") String platform);

    /**
     * Find creators needing ingestion (no embedding).
     */
    @Query("SELECT c FROM Creator c WHERE c.profileEmbedding IS NULL AND c.ingestionStatus = 'pending'")
    List<Creator> findPendingIngestion();
}
