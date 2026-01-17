package com.hermes.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.hermes.domain.grading.ChannelStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;

/**
 * Service for enriching creators with YouTube channel statistics.
 * Fetches subscriber count, view count, video count, and channel age.
 */
@Service
public class YouTubeEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeEnrichmentService.class);
    private static final int BATCH_SIZE = 50; // YouTube API max

    private final String apiKey;
    private final YouTube youtube;

    public YouTubeEnrichmentService(@Value("${hermes.youtube.api-key}") String apiKey)
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
     * Fetches channel statistics for a list of channel IDs.
     * Batches requests to respect API limits.
     *
     * @param channelIds List of YouTube channel IDs
     * @return Map of channelId to ChannelStatistics
     */
    public Map<String, ChannelStatistics> enrichChannels(List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ChannelStatistics> results = new HashMap<>();

        // Process in batches
        for (int i = 0; i < channelIds.size(); i += BATCH_SIZE) {
            List<String> batch = channelIds.subList(
                    i,
                    Math.min(i + BATCH_SIZE, channelIds.size()));

            try {
                Map<String, ChannelStatistics> batchResults = fetchBatch(batch);
                results.putAll(batchResults);
            } catch (IOException e) {
                log.error("[Enrichment] Failed to fetch batch: {}", e.getMessage());
            }
        }

        log.info("[Enrichment] Enriched {} channels", results.size());
        return results;
    }

    /**
     * Fetches a single batch of channel statistics.
     */
    private Map<String, ChannelStatistics> fetchBatch(List<String> channelIds) throws IOException {
        YouTube.Channels.List request = youtube.channels()
                .list(List.of("statistics", "snippet"));
        request.setKey(apiKey);
        request.setId(channelIds);

        ChannelListResponse response = request.execute();
        List<Channel> channels = response.getItems();

        if (channels == null) {
            return Collections.emptyMap();
        }

        Map<String, ChannelStatistics> results = new HashMap<>();
        for (Channel channel : channels) {
            ChannelStatistics stats = mapToStatistics(channel);
            if (stats != null) {
                results.put(channel.getId(), stats);
            }
        }

        return results;
    }

    /**
     * Maps a YouTube Channel to ChannelStatistics.
     */
    private ChannelStatistics mapToStatistics(Channel channel) {
        var stats = channel.getStatistics();
        var snippet = channel.getSnippet();

        if (stats == null) {
            return null;
        }

        long subscribers = stats.getSubscriberCount() != null
                ? stats.getSubscriberCount().longValue()
                : 0;
        long views = stats.getViewCount() != null
                ? stats.getViewCount().longValue()
                : 0;
        long videos = stats.getVideoCount() != null
                ? stats.getVideoCount().longValue()
                : 0;

        Instant publishedAt = null;
        if (snippet != null && snippet.getPublishedAt() != null) {
            publishedAt = Instant.ofEpochMilli(snippet.getPublishedAt().getValue());
        }

        return new ChannelStatistics(
                channel.getId(),
                subscribers,
                views,
                videos,
                publishedAt);
    }
}
