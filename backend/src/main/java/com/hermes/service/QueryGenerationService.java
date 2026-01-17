package com.hermes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.domain.dto.QuerySearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryGenerationService {

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QueryGenerationService(@Value("${hermes.cohere.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public QuerySearchResult generateQueries(String genre) {
        String normalizedGenre = normalize(genre);

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

            System.out.println("[Cohere] Sending request for genre: " + normalizedGenre);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cohere.ai/v1/chat"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("[Cohere] Response status: " + response.statusCode());

            if (response.statusCode() != 200) {
                System.err.println("[Cohere] API error response: " + response.body());
                return createFallbackResult(normalizedGenre);
            }

            System.out.println(
                    "[Cohere] Response body: " + response.body().substring(0, Math.min(500, response.body().length())));

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("text").asText();

            System.out.println("[Cohere] Extracted text: " + text);

            List<String> queries = Arrays.stream(text.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.replaceAll("^[-*\\d.]+\\s*", "")) // Remove common list markers
                    .distinct()
                    .collect(Collectors.toList());

            System.out.println("[Cohere] Parsed queries: " + queries);

            // Fallback if LLM fails to provide enough queries
            if (queries.isEmpty()) {
                System.out.println("[Cohere] No queries parsed, using fallback");
                return createFallbackResult(normalizedGenre);
            }

            return new QuerySearchResult(
                    normalizedGenre,
                    queries,
                    queries.size(),
                    Instant.now().toEpochMilli());

        } catch (Exception e) {
            System.err.println("[Cohere] Query generation failed: " + e.getMessage());
            e.printStackTrace();
            return createFallbackResult(normalizedGenre);
        }
    }

    private QuerySearchResult createFallbackResult(String normalizedGenre) {
        List<String> fallbackQueries = List.of(
                normalizedGenre,
                normalizedGenre + " creator",
                normalizedGenre + " channel",
                normalizedGenre + " youtuber");
        return new QuerySearchResult(
                normalizedGenre,
                fallbackQueries,
                fallbackQueries.size(),
                Instant.now().toEpochMilli());
    }

    private String normalize(String genre) {
        if (genre == null)
            return "";
        return genre.trim().toLowerCase();
    }
}
