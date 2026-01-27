package com.hermes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.cache.QueryDigestService;
import com.hermes.cache.QueryNormalizer;
import com.hermes.domain.dto.QuerySearchResult;
import com.hermes.governor.TokenGovernor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query generation service with two-level caching and token budget control.
 * Cache hit: 0 tokens consumed
 * Cache miss + budget OK: LLM call, result cached
 * Cache miss + over budget: Deterministic fallback
 */
@Service
public class QueryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QueryGenerationService.class);
    private static final int ESTIMATED_TOKENS_PER_QUERY = 300;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final QueryDigestService cacheService;
    private final QueryNormalizer normalizer;
    private final TokenGovernor tokenGovernor;

    public QueryGenerationService(
            @Value("${hermes.cohere.api-key}") String apiKey,
            QueryDigestService cacheService,
            QueryNormalizer normalizer,
            TokenGovernor tokenGovernor) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.cacheService = cacheService;
        this.normalizer = normalizer;
        this.tokenGovernor = tokenGovernor;
    }

    public QuerySearchResult generateQueries(String genre) {
        String normalizedGenre = normalizer.normalize(genre);
        if (normalizedGenre.isEmpty()) {
            normalizedGenre = genre.trim().toLowerCase();
        }

        // PHASE 1: Check cache first (L1 + L2)
        Optional<QueryDigestService.CachedQueryResult> cached = cacheService.get(genre);
        if (cached.isPresent()) {
            log.info("[QueryGen] CACHE HIT for: {} (0 tokens)", normalizedGenre);
            return new QuerySearchResult(
                    normalizedGenre,
                    cached.get().queries(),
                    cached.get().queries().size(),
                    Instant.now().toEpochMilli());
        }

        // PHASE 2: Check token budget
        var budgetDecision = tokenGovernor.checkBudget(ESTIMATED_TOKENS_PER_QUERY);
        if (!budgetDecision.isAllowed()) {
            log.info("[QueryGen] Budget action: {} - using fallback for: {}",
                    budgetDecision.action(), normalizedGenre);
            return createFallbackResult(normalizedGenre);
        }

        // PHASE 3: Call LLM (cache miss + budget OK)
        return callLLMAndCache(genre, normalizedGenre);
    }

    private QuerySearchResult callLLMAndCache(String originalGenre, String normalizedGenre) {
        // Priority queries: always include exact match + variants
        List<String> priorityQueries = List.of(
                normalizedGenre,
                normalizedGenre + " official",
                normalizedGenre + " channel");

        try {
            String prompt = String.format(
                    "Generate 6-8 short, high-signal YouTube search queries for discovering channels in the genre: '%s'. "
                            +
                            "Queries should be concise (2-4 words). Return ONLY a list of queries, one per line, no numbering, no preamble.",
                    normalizedGenre);

            String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", "command-r-08-2024",
                    "message", prompt,
                    "temperature", 0.3));

            log.debug("[QueryGen] LLM request for: {}", normalizedGenre);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.ai/v1/chat"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[QueryGen] LLM error: {}", response.body());
                return createFallbackResult(normalizedGenre);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("text").asText();

            // Record token usage
            int tokensUsed = root.path("meta").path("billed_units").path("input_tokens").asInt(0) +
                    root.path("meta").path("billed_units").path("output_tokens").asInt(0);
            if (tokensUsed == 0) {
                tokensUsed = ESTIMATED_TOKENS_PER_QUERY; // Fallback estimate
            }
            tokenGovernor.recordUsage(tokensUsed);

            List<String> aiQueries = Arrays.stream(text.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.replaceAll("^[-*\\d.]+\\s*", ""))
                    .distinct()
                    .collect(Collectors.toList());

            // Combine priority + AI queries
            List<String> allQueries = new java.util.ArrayList<>(priorityQueries);
            for (String aiQuery : aiQueries) {
                if (!priorityQueries.contains(aiQuery.toLowerCase()) &&
                        !priorityQueries.stream().anyMatch(p -> p.equalsIgnoreCase(aiQuery))) {
                    allQueries.add(aiQuery);
                }
            }

            // Cache the result
            cacheService.put(originalGenre, allQueries, tokensUsed);
            log.info("[QueryGen] LLM call complete, cached {} queries ({} tokens)",
                    allQueries.size(), tokensUsed);

            return new QuerySearchResult(
                    normalizedGenre,
                    allQueries,
                    allQueries.size(),
                    Instant.now().toEpochMilli());

        } catch (Exception e) {
            log.error("[QueryGen] LLM call failed: {}", e.getMessage());
            return createFallbackResult(normalizedGenre);
        }
    }

    /**
     * Deterministic fallback - no LLM tokens consumed.
     */
    private QuerySearchResult createFallbackResult(String normalizedGenre) {
        List<String> fallbackQueries = List.of(
                normalizedGenre,
                normalizedGenre + " official",
                normalizedGenre + " channel",
                normalizedGenre + " youtuber",
                normalizedGenre + " creator",
                normalizedGenre + " best");

        // Cache fallback result too (with 0 token cost)
        cacheService.put(normalizedGenre, fallbackQueries, 0);

        return new QuerySearchResult(
                normalizedGenre,
                fallbackQueries,
                fallbackQueries.size(),
                Instant.now().toEpochMilli());
    }
}
