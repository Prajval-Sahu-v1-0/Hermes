package com.hermes.governor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * YouTube API quota governor.
 * Tracks daily usage and enforces hard limits.
 * 
 * YouTube quota costs:
 * - search.list: 100 units per call
 * - channels.list: 1 unit per channel (up to 50 per call)
 * 
 * Default daily limit: 10,000 units
 */
@Service
public class YouTubeQuotaGovernor {

    private static final Logger log = LoggerFactory.getLogger(YouTubeQuotaGovernor.class);

    // API costs
    public static final int SEARCH_LIST_COST = 100;
    public static final int CHANNELS_LIST_COST_PER_CALL = 1;

    @Value("${hermes.youtube.daily-quota:10000}")
    private int dailyQuota;

    @Value("${hermes.youtube.downgrade-threshold:0.8}")
    private double downgradeThreshold;

    // Thread-safe daily tracking
    private final AtomicInteger dailyUsage = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentDate = new AtomicReference<>(LocalDate.now());

    /**
     * Checks if a YouTube API request can proceed.
     * 
     * @param estimatedCost Estimated quota cost for this request
     * @return Decision with allowed action
     */
    public QuotaDecision checkQuota(int estimatedCost) {
        resetIfNewDay();

        int currentUsage = dailyUsage.get();
        double usageRatio = (double) currentUsage / dailyQuota;

        // Would exceed daily limit?
        if (currentUsage + estimatedCost > dailyQuota) {
            log.warn("[YouTubeQuota] Daily quota exhausted: {} + {} > {}",
                    currentUsage, estimatedCost, dailyQuota);
            return new QuotaDecision(
                    QuotaAction.REJECT,
                    currentUsage,
                    dailyQuota,
                    "Daily quota exhausted");
        }

        // Above 90%? Minimal mode
        if (usageRatio >= 0.9) {
            log.info("[YouTubeQuota] Critical quota level: {:.1f}%", usageRatio * 100);
            return new QuotaDecision(
                    QuotaAction.REDUCE_RESULTS,
                    currentUsage,
                    dailyQuota,
                    "Critical quota - reduce results to 20");
        }

        // Above downgrade threshold?
        if (usageRatio >= downgradeThreshold) {
            log.info("[YouTubeQuota] Above threshold: {:.1f}% - reducing queries", usageRatio * 100);
            return new QuotaDecision(
                    QuotaAction.REDUCE_QUERIES,
                    currentUsage,
                    dailyQuota,
                    "Reducing queries to preserve quota");
        }

        return new QuotaDecision(
                QuotaAction.ALLOW,
                currentUsage,
                dailyQuota,
                "Within quota");
    }

    /**
     * Estimates quota cost for a search operation.
     * 
     * @param queryCount         Number of search queries
     * @param maxResultsPerQuery Max results per query
     * @return Estimated total quota cost
     */
    public int estimateCost(int queryCount, int maxResultsPerQuery) {
        // search.list = 100 units per query
        int searchCost = queryCount * SEARCH_LIST_COST;

        // channels.list = 1 unit per call (batches up to 50 channels)
        // Worst case: each query returns maxResults channels
        int totalChannels = queryCount * maxResultsPerQuery;
        int channelBatches = (int) Math.ceil(totalChannels / 50.0);
        int channelCost = channelBatches * CHANNELS_LIST_COST_PER_CALL;

        return searchCost + channelCost;
    }

    /**
     * Records actual quota usage after API call.
     * 
     * @param cost Actual quota consumed
     */
    public void recordUsage(int cost) {
        resetIfNewDay();
        int newTotal = dailyUsage.addAndGet(cost);
        log.debug("[YouTubeQuota] Recorded {} units, daily total: {}/{}", cost, newTotal, dailyQuota);
    }

    /**
     * Returns current usage statistics.
     */
    public QuotaStats getStats() {
        resetIfNewDay();
        int current = dailyUsage.get();
        return new QuotaStats(
                current,
                dailyQuota,
                (double) current / dailyQuota,
                dailyQuota - current,
                currentDate.get());
    }

    /**
     * Resets counter if it's a new day (PST timezone used by YouTube).
     */
    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        LocalDate stored = currentDate.get();

        if (!today.equals(stored)) {
            if (currentDate.compareAndSet(stored, today)) {
                int previousUsage = dailyUsage.getAndSet(0);
                log.info("[YouTubeQuota] New day reset. Previous day usage: {} units", previousUsage);
            }
        }
    }

    /**
     * Quota decision actions.
     */
    public enum QuotaAction {
        ALLOW, // Full access
        REDUCE_QUERIES, // Cap queries at 3
        REDUCE_RESULTS, // Cap maxResults at 20
        REJECT // No API calls allowed
    }

    /**
     * Quota decision record.
     */
    public record QuotaDecision(
            QuotaAction action,
            int currentUsage,
            int dailyLimit,
            String reason) {

        public boolean isAllowed() {
            return action != QuotaAction.REJECT;
        }

        public int getMaxQueries() {
            return switch (action) {
                case ALLOW -> 5;
                case REDUCE_QUERIES -> 3;
                case REDUCE_RESULTS -> 2;
                case REJECT -> 0;
            };
        }

        public int getMaxResults() {
            return switch (action) {
                case ALLOW -> 50;
                case REDUCE_QUERIES -> 50;
                case REDUCE_RESULTS -> 20;
                case REJECT -> 0;
            };
        }
    }

    /**
     * Quota statistics record.
     */
    public record QuotaStats(
            int unitsUsed,
            int dailyLimit,
            double usageRatio,
            int remainingUnits,
            LocalDate date) {
    }
}
