package com.hermes.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.hermes.domain.dto.QueryResultsMap;
import com.hermes.domain.model.CreatorProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase Two: YouTube Channel Search Service.
 * 
 * Searches YouTube for channels based on a list of queries.
 * Each query is searched independently and results are NOT merged or
 * deduplicated.
 */
@Service
public class YouTubeSearchService {

    private final String apiKey;
    private final YouTube youtube;

    public YouTubeSearchService(@Value("${hermes.youtube.api-key}") String apiKey)
            throws GeneralSecurityException, IOException {
        this.apiKey = apiKey;
        this.youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName("hermes-backend")
                .build();
    }

    /**
     * Searches YouTube for channels using each query independently.
     * 
     * @param queries       List of search queries from Phase One
     * @param limitPerQuery Maximum number of channels to fetch per query (max 50)
     * @return QueryResultsMap preserving query-to-results association
     */
    public QueryResultsMap searchChannels(List<String> queries, int limitPerQuery) {
        Map<String, List<CreatorProfile>> results = new LinkedHashMap<>();
        int limit = Math.min(limitPerQuery, 50); // YouTube API max is 50

        for (String query : queries) {
            System.out.println("[YouTubeSearch] Searching for: " + query);
            List<CreatorProfile> profiles = searchForQuery(query, limit);
            results.put(query, profiles);
            System.out.println("[YouTubeSearch] Found " + profiles.size() + " channels for: " + query);
        }

        return QueryResultsMap.of(results);
    }

    /**
     * Searches YouTube for channels matching a single query.
     * 
     * @param query The search query
     * @param limit Maximum number of results (1-50)
     * @return List of CreatorProfile, empty list on error
     */
    public List<CreatorProfile> searchForQuery(String query, int limit) {
        try {
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
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            if (channelIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 3. Enrich channels with statistics
            YouTube.Channels.List channelList = youtube.channels().list(List.of("snippet", "statistics"));
            channelList.setKey(apiKey);
            channelList.setId(channelIds);

            ChannelListResponse channelResponse = channelList.execute();
            List<Channel> channels = channelResponse.getItems();

            if (channels == null) {
                return Collections.emptyList();
            }

            return channels.stream()
                    .map(this::mapToProfile)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            System.err.println("[YouTubeSearch] API Error for query '" + query + "': " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Maps a YouTube Channel to our internal CreatorProfile domain model.
     */
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

        String avatarUrl = "";
        if (snippet.getThumbnails() != null && snippet.getThumbnails().getDefault() != null) {
            avatarUrl = snippet.getThumbnails().getDefault().getUrl();
        }

        return new CreatorProfile(
                channel.getId(),
                snippet.getCustomUrl(),
                snippet.getTitle(),
                snippet.getDescription(),
                avatarUrl,
                subscribers,
                videos,
                views,
                List.of(),
                snippet.getCountry(),
                new HashMap<>(),
                0.0);
    }
}
