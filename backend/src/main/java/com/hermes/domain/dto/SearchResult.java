package com.hermes.domain.dto;

import com.hermes.domain.grading.GradedCreator;
import com.hermes.domain.model.CreatorProfile;
import java.util.List;

/**
 * Search result containing:
 * - Phase One: query generation info
 * - Phase Two: raw channel results
 * - Phase Three: graded creators
 * - Phase Four: paginated, ranked results
 */
public record SearchResult(
                List<CreatorProfile> creators,
                QuerySearchResult queryInfo,
                QueryResultsMap channelResults,
                List<GradedCreator> gradedCreators,
                PaginatedResult paginatedResults) {
        /**
         * Constructor for Phase Three (before pagination).
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo,
                        QueryResultsMap channelResults, List<GradedCreator> gradedCreators) {
                this(creators, queryInfo, channelResults, gradedCreators, null);
        }

        /**
         * Constructor for Phase One/Two only.
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo,
                        QueryResultsMap channelResults) {
                this(creators, queryInfo, channelResults, List.of(), null);
        }

        /**
         * Constructor for Phase One only.
         */
        public SearchResult(List<CreatorProfile> creators, QuerySearchResult queryInfo) {
                this(creators, queryInfo, null, List.of(), null);
        }
}
