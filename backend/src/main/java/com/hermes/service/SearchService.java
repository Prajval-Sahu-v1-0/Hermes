package com.hermes.service;

import com.hermes.domain.dto.PaginatedResult;
import com.hermes.domain.dto.QueryResultsMap;
import com.hermes.domain.dto.SearchRequest;
import com.hermes.domain.dto.SearchResult;
import com.hermes.domain.entity.Creator;
import com.hermes.domain.grading.GradedCreator;
import com.hermes.domain.grading.GradingCriteria;
import com.hermes.repository.CreatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SearchService {

        private static final Logger log = LoggerFactory.getLogger(SearchService.class);

        private final QueryGenerationService queryGenerationService;
        private final YouTubeSearchService youTubeSearchService;
        private final CreatorPersistenceService creatorPersistenceService;
        private final CreatorRepository creatorRepository;
        private final GradingService gradingService;
        private final RankingService rankingService;

        public SearchService(
                        QueryGenerationService queryGenerationService,
                        YouTubeSearchService youTubeSearchService,
                        CreatorPersistenceService creatorPersistenceService,
                        CreatorRepository creatorRepository,
                        GradingService gradingService,
                        RankingService rankingService) {
                this.queryGenerationService = queryGenerationService;
                this.youTubeSearchService = youTubeSearchService;
                this.creatorPersistenceService = creatorPersistenceService;
                this.creatorRepository = creatorRepository;
                this.gradingService = gradingService;
                this.rankingService = rankingService;
        }

        public SearchResult performSearch(SearchRequest request) {
                // Phase One: Generate intent-based queries using Cohere AI
                var queryResult = queryGenerationService.generateQueries(request.genre());

                // Phase Two: Search YouTube for channels using each generated query
                QueryResultsMap channelResults = youTubeSearchService.searchChannels(
                                queryResult.queries(),
                                50);

                // Phase 2.5: Persist discovered creators to database
                creatorPersistenceService.persistDiscoveredCreators(
                                channelResults.queryResults(),
                                request.genre());

                // Phase Three: Grade creators (per-query)
                Map<String, List<GradedCreator>> gradedByQuery = gradeCreatorsByQuery(request, queryResult.queries());

                // Phase Four: Merge, deduplicate, rank, and paginate
                PaginatedResult paginatedResults = rankingService.rankAndPaginate(
                                gradedByQuery,
                                request.page(),
                                5 // Default page size
                );

                return new SearchResult(
                                List.of(),
                                queryResult,
                                channelResults,
                                paginatedResults.creators(), // For backward compatibility
                                paginatedResults);
        }

        /**
         * Grades creators grouped by query for proper deduplication.
         */
        private Map<String, List<GradedCreator>> gradeCreatorsByQuery(SearchRequest request, List<String> queries) {
                // Fetch persisted creators for this genre
                List<Creator> allCreators = creatorRepository.findAll().stream()
                                .filter(c -> c.getBaseGenre().equalsIgnoreCase(request.genre()))
                                .filter(c -> c.getStatus() == Creator.Status.ACTIVE)
                                .limit(500) // Increase limit for multi-query handling
                                .toList();

                if (allCreators.isEmpty()) {
                        log.info("[Search] No creators found for genre: {}", request.genre());
                        return Map.of();
                }

                // Create grading criteria from user filters
                GradingCriteria criteria = gradingService.createCriteria(
                                request.genre(),
                                request.filters() != null ? request.filters() : Map.of());

                // Group creators by their origin query
                Map<String, List<Creator>> creatorsByQuery = new HashMap<>();
                for (Creator creator : allCreators) {
                        String originQuery = creator.getOriginQuery();
                        if (originQuery != null) {
                                creatorsByQuery.computeIfAbsent(originQuery, k -> new ArrayList<>()).add(creator);
                        }
                }

                // Grade each query's creators
                Map<String, List<GradedCreator>> gradedByQuery = new LinkedHashMap<>();
                for (String query : queries) {
                        List<Creator> queryCreators = creatorsByQuery.getOrDefault(query, List.of());
                        if (!queryCreators.isEmpty()) {
                                List<GradedCreator> graded = gradingService.gradeCreators(queryCreators, criteria);
                                gradedByQuery.put(query, graded);
                        }
                }

                return gradedByQuery;
        }
}
