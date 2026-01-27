package com.hermes.repository;

import com.hermes.domain.entity.QueryEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for query embedding persistence.
 */
@Repository
public interface QueryEmbeddingRepository extends JpaRepository<QueryEmbedding, String> {

    /**
     * Find non-expired embedding by digest key.
     */
    @Query("SELECT q FROM QueryEmbedding q WHERE q.digestKey = :key AND q.expiresAt > :now")
    Optional<QueryEmbedding> findValidByDigestKey(@Param("key") String digestKey, @Param("now") Instant now);

    /**
     * Delete expired entries.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM QueryEmbedding q WHERE q.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
