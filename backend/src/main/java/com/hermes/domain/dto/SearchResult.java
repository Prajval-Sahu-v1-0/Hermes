package com.hermes.domain.dto;

import com.hermes.domain.grading.GradedCreator;
import com.hermes.domain.model.CreatorProfile;
import java.util.List;
import java.util.UUID;

/**
 * Search result containing:
 * - Phase One: query generation info
 * - Phase Two: raw channel results
 * - Phase Three: graded creators
 * - Phase Four: paginated, ranked results
 * - Session info for pagination without API calls
 */
public record SearchResult(
                List<CreatorProfile> creators,
                QuerySearchResult queryInfo,
                QueryResultsMap channelResults,
                List<GradedCreator> gradedCreators,
                PaginatedResult paginatedResults,
                UUID sessionId,
                boolean fromCache,
                int youtubeQuotaUsed) {

        /**
         * Constructor for backward compatibility.
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo,
                        QueryResultsMap channelResults, List<GradedCreator> gradedCreators,
                        PaginatedResult paginatedResults) {
                this(creators, queryInfo, channelResults, gradedCreators, paginatedResults,
                                null, false, 0);
        }

        /**
         * Constructor for Phase Three (before pagination).
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo,
                        QueryResultsMap channelResults, List<GradedCreator> gradedCreators) {
                this(creators, queryInfo, channelResults, gradedCreators, null, null, false, 0);
        }

        /**
         * Constructor for Phase One/Two only.
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo,
                        QueryResultsMap channelResults) {
                this(creators, queryInfo, channelResults, List.of(), null, null, false, 0);
        }

        /**
         * Constructor for Phase One only.
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo) {
                this(creators, queryInfo, null, List.of(), null, null, false, 0);
        }

        /**
         * Factory method for session-based results (zero-API pagination).
         */
        public static SearchResult fromSession(UUID sessionId, String query,
                        PaginatedResult paginatedResults, boolean fromCache, int quotaUsed) {
                return new SearchResult(
                                List.of(),
                                null,
                                null,
                                paginatedResults.creators(),
                                paginatedResults,
                                sessionId,
                                fromCache,
                                quotaUsed);
        }

        /**
         * Factory method for empty results.
         */
        public static SearchResult empty() {
                return new SearchResult(
                                List.of(),
                                null,
                                null,
                                List.of(),
                                PaginatedResult.empty(0, 10),
                                null,
                                false,
                                0);
        }
}
