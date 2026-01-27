package com.hermes.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.governor.TokenGovernor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Cohere embedding generation service.
 * Generates 1024-dimensional embeddings for queries and creator profiles.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String COHERE_EMBED_URL = "https://api.cohere.ai/v1/embed";
    private static final String EMBED_MODEL = "embed-english-v3.0";
    private static final int EMBEDDING_DIMS = 1024;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenGovernor tokenGovernor;

    public EmbeddingService(
            @Value("${hermes.cohere.api-key}") String apiKey,
            TokenGovernor tokenGovernor) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.tokenGovernor = tokenGovernor;
    }

    /**
     * Generates embedding for a single text input.
     * 
     * @param text      Text to embed
     * @param inputType "search_query" or "search_document"
     * @return 1024-dimensional embedding array
     */
    public double[] embed(String text, InputType inputType) {
        return embedBatch(List.of(text), inputType).get(0);
    }

    /**
     * Generates embeddings for multiple texts in a single API call.
     * 
     * @param texts     List of texts to embed (max 96 per call)
     * @param inputType Type of input
     * @return List of embedding arrays
     */
    public List<double[]> embedBatch(List<String> texts, InputType inputType) {
        if (texts.isEmpty()) {
            return List.of();
        }

        // Estimate tokens (rough: 4 chars per token)
        int estimatedTokens = texts.stream().mapToInt(String::length).sum() / 4;

        // Check budget
        var decision = tokenGovernor.checkBudget(estimatedTokens);
        if (!decision.canUseLLM()) {
            log.warn("[Embedding] Budget exceeded, returning zero embeddings");
            return texts.stream().map(t -> new double[EMBEDDING_DIMS]).toList();
        }

        try {
            String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", EMBED_MODEL,
                    "texts", texts,
                    "input_type", inputType.getValue(),
                    "truncate", "END"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COHERE_EMBED_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[Embedding] API error: {}", response.body());
                return texts.stream().map(t -> new double[EMBEDDING_DIMS]).toList();
            }

            JsonNode root = objectMapper.readTree(response.body());

            // Record token usage
            int tokensUsed = root.path("meta").path("billed_units").path("input_tokens").asInt(estimatedTokens);
            tokenGovernor.recordUsage(tokensUsed);

            // Parse embeddings
            JsonNode embeddingsNode = root.path("embeddings");
            return parseEmbeddings(embeddingsNode, texts.size());

        } catch (Exception e) {
            log.error("[Embedding] Failed to generate embeddings: {}", e.getMessage());
            return texts.stream().map(t -> new double[EMBEDDING_DIMS]).toList();
        }
    }

    private List<double[]> parseEmbeddings(JsonNode embeddingsNode, int expectedCount) {
        if (!embeddingsNode.isArray()) {
            return java.util.stream.IntStream.range(0, expectedCount)
                    .mapToObj(i -> new double[EMBEDDING_DIMS])
                    .toList();
        }

        java.util.List<double[]> result = new java.util.ArrayList<>();
        for (JsonNode embNode : embeddingsNode) {
            double[] embedding = new double[EMBEDDING_DIMS];
            for (int i = 0; i < Math.min(embNode.size(), EMBEDDING_DIMS); i++) {
                embedding[i] = embNode.get(i).asDouble();
            }
            result.add(embedding);
        }
        return result;
    }

    /**
     * Computes cosine similarity between two embeddings.
     */
    public double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embedding dimensions must match");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Input type for Cohere embeddings.
     */
    public enum InputType {
        SEARCH_QUERY("search_query"),
        SEARCH_DOCUMENT("search_document");

        private final String value;

        InputType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
