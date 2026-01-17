package com.hermes.service;

import com.hermes.domain.dto.PaginatedResult;
import com.hermes.domain.grading.CreatorScore;
import com.hermes.domain.grading.GradedCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for merging, deduplicating, ranking, and paginating graded creators.
 * 
 * Pure in-memory transformations with no external dependencies.
 * Deterministic and side-effect free.
 */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final int DEFAULT_PAGE_SIZE = 5;

    /**
     * Merges, deduplicates, ranks, and paginates graded creators.
     * 
     * @param queryResults Map of query to graded creators
     * @param page         Page index (0-based)
     * @param pageSize     Number of results per page
     * @return Paginated, ranked, deduplicated creator list
     */
    public PaginatedResult rankAndPaginate(
            Map<String, List<GradedCreator>> queryResults,
            int page,
            int pageSize) {

        if (queryResults == null || queryResults.isEmpty()) {
            return PaginatedResult.empty(page, pageSize);
        }

        int effectivePageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        int effectivePage = Math.max(0, page);

        // Step 1: Merge all creators across queries
        List<GradedCreator> allCreators = mergeCreators(queryResults);
        log.debug("[Ranking] Merged {} creators from {} queries",
                allCreators.size(), queryResults.size());

        // Step 2: Deduplicate by channelId (keep highest score, merge labels)
        List<GradedCreator> deduplicated = deduplicateCreators(allCreators);
        log.debug("[Ranking] Deduplicated to {} unique creators", deduplicated.size());

        // Step 3: Sort by finalScore (descending)
        List<GradedCreator> ranked = rankCreators(deduplicated);

        // Step 4: Paginate
        return paginate(ranked, effectivePage, effectivePageSize);
    }

    /**
     * Convenience method with default page size.
     */
    public PaginatedResult rankAndPaginate(
            Map<String, List<GradedCreator>> queryResults,
            int page) {
        return rankAndPaginate(queryResults, page, DEFAULT_PAGE_SIZE);
    }

    /**
     * Merges all graded creators from multiple queries into a single list.
     */
    private List<GradedCreator> mergeCreators(Map<String, List<GradedCreator>> queryResults) {
        return queryResults.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Deduplicates creators by channelId.
     * - Retains the instance with the highest finalScore
     * - Merges labels uniquely
     */
    private List<GradedCreator> deduplicateCreators(List<GradedCreator> creators) {
        Map<String, GradedCreator> best = new LinkedHashMap<>();
        Map<String, Set<String>> mergedLabels = new HashMap<>();

        for (GradedCreator creator : creators) {
            String id = creator.channelId();

            // Track all labels for this channel
            mergedLabels.computeIfAbsent(id, k -> new LinkedHashSet<>())
                    .addAll(creator.labels());

            // Keep the one with highest score
            GradedCreator existing = best.get(id);
            if (existing == null || creator.getFinalScore() > existing.getFinalScore()) {
                best.put(id, creator);
            }
        }

        // Rebuild creators with merged labels
        return best.entrySet().stream()
                .map(entry -> {
                    GradedCreator original = entry.getValue();
                    Set<String> allLabels = mergedLabels.get(entry.getKey());
                    return new GradedCreator(
                            original.channelId(),
                            original.channelName(),
                            original.description(),
                            original.profileImageUrl(),
                            original.platform(),
                            original.score(),
                            new ArrayList<>(allLabels));
                })
                .collect(Collectors.toList());
    }

    /**
     * Sorts creators by finalScore in descending order.
     * Ties are broken by channelName (alphabetically).
     */
    private List<GradedCreator> rankCreators(List<GradedCreator> creators) {
        return creators.stream()
                .sorted(Comparator
                        .comparingDouble(GradedCreator::getFinalScore).reversed()
                        .thenComparing(GradedCreator::channelName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    /**
     * Applies pagination to the ranked list.
     */
    private PaginatedResult paginate(List<GradedCreator> ranked, int page, int pageSize) {
        int totalResults = ranked.size();
        int totalPages = (int) Math.ceil((double) totalResults / pageSize);

        // Clamp page to valid range
        int effectivePage = Math.min(page, Math.max(0, totalPages - 1));

        int startIndex = effectivePage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalResults);

        List<GradedCreator> pageContent;
        if (startIndex >= totalResults) {
            pageContent = List.of();
        } else {
            pageContent = ranked.subList(startIndex, endIndex);
        }

        log.info("[Ranking] Returning page {} of {} ({} results)",
                effectivePage + 1, totalPages, pageContent.size());

        return new PaginatedResult(
                pageContent,
                effectivePage,
                pageSize,
                totalResults,
                totalPages);
    }
}
