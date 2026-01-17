package com.hermes.platform.impl;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.hermes.domain.model.CreatorProfile;
import com.hermes.platform.PlatformAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class YouTubeAdapter implements PlatformAdapter {

    private final String apiKey;
    private final YouTube youtube;

    public YouTubeAdapter(@Value("${hermes.youtube.api-key}") String apiKey)
            throws GeneralSecurityException, IOException {
        this.apiKey = apiKey;
        this.youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName("hermes-backend")
                .build();
    }

    @Override
    public String getPlatformName() {
        return "youtube";
    }

    @Override
    public List<CreatorProfile> searchCreators(String query, int limit) {
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

        } catch (IOException e) {
            // Placeholder for quota handling and error logging
            System.err.println("YouTube API Error: " + e.getMessage());
            return Collections.emptyList();
        }
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

        return new CreatorProfile(
                channel.getId(),
                snippet.getCustomUrl(), // Handle/Username
                snippet.getTitle(),
                snippet.getDescription(),
                snippet.getThumbnails().getDefault().getUrl(),
                subscribers,
                videos,
                views,
                List.of(), // Categories to be enriched if needed
                snippet.getCountry(),
                new HashMap<>(), // Labels set later by ScoringEngine/Service
                0.0);
    }
}
