package com.hermes.repository;

import com.hermes.domain.entity.SearchSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SearchSession entity.
 * Provides cache lookup and session lifecycle management.
 */
@Repository
public interface SearchSessionRepository extends JpaRepository<SearchSession, UUID> {

        /**
         * Find a valid (non-expired) session by query digest and platform.
         * This is the primary cache lookup method.
         */
        @Query("SELECT s FROM SearchSession s WHERE s.queryDigest = :digest " +
                        "AND s.platform = :platform AND s.expiresAt > :now")
        Optional<SearchSession> findValidSession(
                        @Param("digest") String queryDigest,
                        @Param("platform") String platform,
                        @Param("now") Instant now);

        /**
         * Find ANY session by digest and platform (even expired).
         */
        Optional<SearchSession> findByQueryDigestAndPlatform(String queryDigest, String platform);

        /**
         * Check if a valid session exists without loading full entity.
         */
        @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
                        "FROM SearchSession s WHERE s.queryDigest = :digest " +
                        "AND s.platform = :platform AND s.expiresAt > :now")
        boolean existsValidSession(
                        @Param("digest") String queryDigest,
                        @Param("platform") String platform,
                        @Param("now") Instant now);

        /**
         * Update last accessed time for sliding expiration.
         */
        @Modifying
        @Query("UPDATE SearchSession s SET s.lastAccessedAt = :now WHERE s.sessionId = :sessionId")
        void touch(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

        /**
         * Extend session expiration (sliding window).
         */
        @Modifying
        @Query("UPDATE SearchSession s SET s.expiresAt = :newExpiry, s.lastAccessedAt = :now " +
                        "WHERE s.sessionId = :sessionId")
        void extendExpiry(@Param("sessionId") UUID sessionId,
                        @Param("newExpiry") Instant newExpiry,
                        @Param("now") Instant now);

        /**
         * Delete all expired sessions (cleanup job).
         */
        @Modifying
        @Query("DELETE FROM SearchSession s WHERE s.expiresAt < :now")
        int deleteExpiredSessions(@Param("now") Instant now);

        /**
         * Count active sessions for monitoring.
         */
        @Query("SELECT COUNT(s) FROM SearchSession s WHERE s.expiresAt > :now")
        long countActiveSessions(@Param("now") Instant now);
}
