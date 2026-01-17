package com.hermes.domain.dto;

import java.util.List;

/**
 * Represents the structured result of the query generation phase.
 *
 * @param baseGenre  The original genre provided by the user.
 * @param queries    The list of generated search queries.
 * @param queryCount The number of queries generated.
 * @param timestamp  The time at which the queries were generated.
 */
public record QuerySearchResult(
        String baseGenre,
        List<String> queries,
        int queryCount,
        long timestamp) {
}
