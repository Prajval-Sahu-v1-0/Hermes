package com.hermes.repository;

import com.hermes.domain.entity.SearchSessionResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SearchSessionResult.
 * Provides zero-API-call pagination via database slices.
 */
@Repository
public interface SearchSessionResultRepository
                extends JpaRepository<SearchSessionResult, SearchSessionResult.SearchSessionResultId> {

        /**
         * Offset-based pagination (simple, works for most cases).
         * Use Spring Data Pageable for automatic OFFSET/LIMIT.
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.rank")
        List<SearchSessionResult> findBySessionIdPaginated(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);

        /**
         * Cursor-based pagination (preferred for large result sets).
         * More efficient than OFFSET for deep pagination.
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId " +
                        "AND r.rank > :lastRank ORDER BY r.rank")
        List<SearchSessionResult> findAfterRank(
                        @Param("sessionId") UUID sessionId,
                        @Param("lastRank") int lastRank,
                        Pageable pageable);

        /**
         * Count total results for a session.
         */
        @Query("SELECT COUNT(r) FROM SearchSessionResult r WHERE r.sessionId = :sessionId")
        int countBySessionId(@Param("sessionId") UUID sessionId);

        /**
         * Get all results for a session (for complete re-ranking if needed).
         */
        List<SearchSessionResult> findBySessionIdOrderByRank(UUID sessionId);

        /**
         * Delete all results for a session (cascade handled by FK, but explicit is
         * cleaner).
         */
        @Modifying
        @Query("DELETE FROM SearchSessionResult r WHERE r.sessionId = :sessionId")
        void deleteBySessionId(@Param("sessionId") UUID sessionId);

        /**
         * Batch insert helper - checks if results exist.
         */
        @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
                        "FROM SearchSessionResult r WHERE r.sessionId = :sessionId")
        boolean existsBySessionId(@Param("sessionId") UUID sessionId);

        // ===== SORTED PAGINATION QUERIES =====
        // These operate on precomputed, stored values ONLY.
        // No API calls, no ranking recomputation.

        /**
         * Offset-based pagination sorted by final score (default).
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.score DESC, r.rank ASC")
        List<SearchSessionResult> findBySessionIdOrderByScoreDesc(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);

        /**
         * Offset-based pagination sorted by genre relevance.
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.genreRelevance DESC, r.rank ASC")
        List<SearchSessionResult> findBySessionIdOrderByRelevanceDesc(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);

        /**
         * Offset-based pagination sorted by audience fit (subscribers).
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.audienceFit DESC, r.rank ASC")
        List<SearchSessionResult> findBySessionIdOrderBySubscribersDesc(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);

        /**
         * Offset-based pagination sorted by engagement quality.
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.engagementQuality DESC, r.rank ASC")
        List<SearchSessionResult> findBySessionIdOrderByEngagementDesc(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);

        /**
         * Offset-based pagination sorted by activity consistency.
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.activityConsistency DESC, r.rank ASC")
        List<SearchSessionResult> findBySessionIdOrderByActivityDesc(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);

        /**
         * Offset-based pagination sorted by competitiveness score.
         */
        @Query("SELECT r FROM SearchSessionResult r WHERE r.sessionId = :sessionId ORDER BY r.competitivenessScore DESC, r.rank ASC")
        List<SearchSessionResult> findBySessionIdOrderByCompetitivenessDesc(
                        @Param("sessionId") UUID sessionId,
                        Pageable pageable);
}
