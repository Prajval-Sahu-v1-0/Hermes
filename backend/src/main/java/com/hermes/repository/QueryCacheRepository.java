package com.hermes.repository;

import com.hermes.domain.entity.QueryCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for L2 query cache persistence.
 */
@Repository
public interface QueryCacheRepository extends JpaRepository<QueryCache, String> {

    /**
     * Find non-expired cache entry by digest key.
     */
    @Query("SELECT q FROM QueryCache q WHERE q.digestKey = :key AND q.expiresAt > :now")
    Optional<QueryCache> findValidByDigestKey(@Param("key") String digestKey, @Param("now") Instant now);

    /**
     * Increment hit count for analytics.
     */
    @Modifying
    @Transactional
    @Query("UPDATE QueryCache q SET q.hitCount = q.hitCount + 1 WHERE q.digestKey = :key")
    void incrementHitCount(@Param("key") String digestKey);

    /**
     * Delete expired entries (maintenance job).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM QueryCache q WHERE q.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * Get top cached queries by hit count (for analytics/warmup).
     */
    @Query("SELECT q FROM QueryCache q WHERE q.expiresAt > :now ORDER BY q.hitCount DESC")
    List<QueryCache> findTopByHitCount(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}
