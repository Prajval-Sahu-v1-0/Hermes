package com.hermes.platform.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.hermes.domain.model.CreatorProfile;
import com.hermes.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class YouTubeAdapter implements PlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(YouTubeAdapter.class);

    private final List<String> apiKeys;
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);
    private final YouTube youtube;

    public YouTubeAdapter(@Value("${hermes.youtube.api-keys}") String apiKeysConfig)
            throws GeneralSecurityException, IOException {
        // Parse comma-separated API keys
        this.apiKeys = Arrays.stream(apiKeysConfig.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toList());

        if (apiKeys.isEmpty()) {
            throw new IllegalArgumentException("No YouTube API keys configured");
        }

        log.info("[YouTube] Initialized with {} API keys", apiKeys.size());

        this.youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName("hermes-backend")
                .build();
    }

    /**
     * Gets the current API key.
     */
    private String getCurrentApiKey() {
        int index = currentKeyIndex.get() % apiKeys.size();
        return apiKeys.get(index);
    }

    /**
     * Rotates to the next API key.
     * Returns true if there are more keys to try, false if we've exhausted all
     * keys.
     */
    private boolean rotateApiKey() {
        int newIndex = currentKeyIndex.incrementAndGet();
        if (newIndex >= apiKeys.size()) {
            // We've tried all keys, reset for next request cycle
            currentKeyIndex.set(0);
            return false;
        }
        log.warn("[YouTube] Rotating to API key {} of {}", newIndex + 1, apiKeys.size());
        return true;
    }

    /**
     * Checks if the exception is a quota exceeded error.
     */
    private boolean isQuotaExceeded(IOException e) {
        if (e instanceof GoogleJsonResponseException) {
            GoogleJsonResponseException jsonException = (GoogleJsonResponseException) e;
            int statusCode = jsonException.getStatusCode();
            String message = e.getMessage().toLowerCase();
            return statusCode == 403 && (message.contains("quota") || message.contains("exceeded"));
        }
        return false;
    }

    @Override
    public String getPlatformName() {
        return "youtube";
    }

    @Override
    public List<CreatorProfile> searchCreators(String query, int limit) {
        // Track how many keys we've tried
        int attemptedKeys = 0;

        while (attemptedKeys < apiKeys.size()) {
            try {
                return executeSearch(query, limit, getCurrentApiKey());
            } catch (IOException e) {
                if (isQuotaExceeded(e)) {
                    log.warn("[YouTube] Quota exceeded for key {}, rotating...", currentKeyIndex.get() + 1);
                    attemptedKeys++;
                    if (!rotateApiKey() || attemptedKeys >= apiKeys.size()) {
                        log.error("[YouTube] All API keys exhausted!");
                        return Collections.emptyList();
                    }
                } else {
                    log.error("[YouTube] API Error: {}", e.getMessage());
                    return Collections.emptyList();
                }
            }
        }

        return Collections.emptyList();
    }

    /**
     * Executes the actual YouTube search with a specific API key.
     */
    private List<CreatorProfile> executeSearch(String query, int limit, String apiKey) throws IOException {
        // 1. Search for channels
        YouTube.Search.List search = youtube.search().list(List.of("snippet"));
        search.setKey(apiKey);
        search.setQ(query);
        search.setType(List.of("channel"));
        search.setMaxResults((long) limit);

        SearchListResponse searchResponse = search.execute();
        List<SearchResult> searchResults = searchResponse.getItems();

        if (searchResults == null || searchResults.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Extract channel IDs for enrichment
        List<String> channelIds = searchResults.stream()
                .map(r -> r.getSnippet().getChannelId())
                .toList();

        // 3. Enrich channels with statistics
        YouTube.Channels.List channelList = youtube.channels().list(List.of("snippet", "statistics"));
        channelList.setKey(apiKey);
        channelList.setId(channelIds);

        ChannelListResponse channelResponse = channelList.execute();
        List<Channel> channels = channelResponse.getItems();

        return channels.stream()
                .map(this::mapToProfile)
                .collect(Collectors.toList());
    }

    private CreatorProfile mapToProfile(Channel channel) {
        var snippet = channel.getSnippet();
        var stats = channel.getStatistics();

        long subscribers = 0;
        long videos = 0;
        long views = 0;

        if (stats != null) {
            subscribers = stats.getSubscriberCount() != null ? stats.getSubscriberCount().longValue() : 0;
            videos = stats.getVideoCount() != null ? stats.getVideoCount().longValue() : 0;
            views = stats.getViewCount() != null ? stats.getViewCount().longValue() : 0;
        }

        // Get highest resolution thumbnail available (maxres > high > medium > default)
        String thumbnailUrl = null;
        var thumbnails = snippet.getThumbnails();
        if (thumbnails != null) {
            if (thumbnails.getMaxres() != null) {
                thumbnailUrl = thumbnails.getMaxres().getUrl();
            } else if (thumbnails.getHigh() != null) {
                thumbnailUrl = thumbnails.getHigh().getUrl();
            } else if (thumbnails.getMedium() != null) {
                thumbnailUrl = thumbnails.getMedium().getUrl();
            } else if (thumbnails.getDefault() != null) {
                thumbnailUrl = thumbnails.getDefault().getUrl();
            }
        }

        return new CreatorProfile(
                channel.getId(),
                snippet.getCustomUrl(), // Handle/Username
                snippet.getTitle(),
                snippet.getDescription(),
                thumbnailUrl,
                subscribers,
                videos,
                views,
                List.of(), // Categories to be enriched if needed
                snippet.getCountry(),
                new HashMap<>(), // Labels set later by ScoringEngine/Service
                0.0);
    }
}
