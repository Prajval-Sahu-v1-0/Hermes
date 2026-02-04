package com.hermes.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.hermes.domain.grading.ChannelStatistics;
import com.hermes.domain.grading.VideoStatistics;
import com.hermes.grading.scorer.EngagementScorer;
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
 * Fetches subscriber count, view count, video count, channel age,
 * and per-video engagement data for behavior-based scoring.
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
     * Includes per-video engagement data for behavior-based scoring.
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

        log.info("[Enrichment] Enriched {} channels with video statistics", results.size());
        return results;
    }

    /**
     * Fetches a single batch of channel statistics with video data.
     */
    private Map<String, ChannelStatistics> fetchBatch(List<String> channelIds) throws IOException {
        // Step 1: Fetch channel info with contentDetails for uploads playlist
        YouTube.Channels.List request = youtube.channels()
                .list(List.of("statistics", "snippet", "contentDetails"));
        request.setKey(apiKey);
        request.setId(channelIds);

        ChannelListResponse response = request.execute();
        List<Channel> channels = response.getItems();

        if (channels == null) {
            return Collections.emptyMap();
        }

        Map<String, ChannelStatistics> results = new HashMap<>();
        for (Channel channel : channels) {
            ChannelStatistics stats = mapToStatisticsWithVideos(channel);
            if (stats != null) {
                results.put(channel.getId(), stats);
            }
        }

        return results;
    }

    /**
     * Maps a YouTube Channel to ChannelStatistics including video engagement data.
     */
    private ChannelStatistics mapToStatisticsWithVideos(Channel channel) {
        var stats = channel.getStatistics();
        var snippet = channel.getSnippet();
        var contentDetails = channel.getContentDetails();

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

        // Step 2: Fetch recent video statistics for engagement scoring
        List<VideoStatistics> recentVideos = null;
        if (contentDetails != null && contentDetails.getRelatedPlaylists() != null) {
            String uploadsPlaylistId = contentDetails.getRelatedPlaylists().getUploads();
            if (uploadsPlaylistId != null) {
                try {
                    recentVideos = fetchRecentVideoStats(uploadsPlaylistId);
                } catch (IOException e) {
                    log.warn("[Enrichment] Failed to fetch videos for channel {}: {}",
                            channel.getId(), e.getMessage());
                }
            }
        }

        return new ChannelStatistics(
                channel.getId(),
                subscribers,
                views,
                videos,
                publishedAt,
                recentVideos);
    }

    /**
     * Fetches statistics for recent videos from an uploads playlist.
     *
     * @param uploadsPlaylistId The channel's uploads playlist ID
     * @return List of VideoStatistics for recent videos, or null if unavailable
     */
    private List<VideoStatistics> fetchRecentVideoStats(String uploadsPlaylistId) throws IOException {
        // Step 2a: Get recent video IDs from uploads playlist
        YouTube.PlaylistItems.List playlistRequest = youtube.playlistItems()
                .list(List.of("snippet"));
        playlistRequest.setKey(apiKey);
        playlistRequest.setPlaylistId(uploadsPlaylistId);
        playlistRequest.setMaxResults((long) EngagementScorer.MAX_RECENT_VIDEOS);

        PlaylistItemListResponse playlistResponse = playlistRequest.execute();
        List<PlaylistItem> items = playlistResponse.getItems();

        if (items == null || items.isEmpty()) {
            return null;
        }

        // Extract video IDs
        List<String> videoIds = items.stream()
                .map(item -> item.getSnippet().getResourceId().getVideoId())
                .filter(Objects::nonNull)
                .toList();

        if (videoIds.isEmpty()) {
            return null;
        }

        // Step 2b: Fetch video statistics
        YouTube.Videos.List videosRequest = youtube.videos()
                .list(List.of("statistics", "snippet"));
        videosRequest.setKey(apiKey);
        videosRequest.setId(videoIds);

        VideoListResponse videosResponse = videosRequest.execute();
        List<Video> videosData = videosResponse.getItems();

        if (videosData == null || videosData.isEmpty()) {
            return null;
        }

        // Map to VideoStatistics records
        List<VideoStatistics> result = new ArrayList<>();
        for (Video video : videosData) {
            var videoStats = video.getStatistics();
            var videoSnippet = video.getSnippet();

            if (videoStats == null) {
                continue;
            }

            long viewCount = videoStats.getViewCount() != null
                    ? videoStats.getViewCount().longValue()
                    : 0;
            long likeCount = videoStats.getLikeCount() != null
                    ? videoStats.getLikeCount().longValue()
                    : 0;
            long commentCount = videoStats.getCommentCount() != null
                    ? videoStats.getCommentCount().longValue()
                    : 0;

            Instant videoPublishedAt = null;
            if (videoSnippet != null && videoSnippet.getPublishedAt() != null) {
                videoPublishedAt = Instant.ofEpochMilli(videoSnippet.getPublishedAt().getValue());
            }

            result.add(new VideoStatistics(
                    video.getId(),
                    viewCount,
                    likeCount,
                    commentCount,
                    videoPublishedAt));
        }

        log.debug("[Enrichment] Fetched {} video stats for playlist {}",
                result.size(), uploadsPlaylistId);

        return result.isEmpty() ? null : result;
    }
}
