package com.hermes.governor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token budget enforcer for LLM API calls.
 * Tracks daily usage and enforces hard limits.
 */
@Service
public class TokenGovernor {

    private static final Logger log = LoggerFactory.getLogger(TokenGovernor.class);

    @Value("${hermes.llm.daily-token-budget:1000000}")
    private int dailyBudget;

    @Value("${hermes.llm.per-request-budget:2000}")
    private int perRequestBudget;

    @Value("${hermes.llm.fallback-threshold:0.9}")
    private double fallbackThreshold;

    // Thread-safe daily tracking
    private final AtomicInteger dailyUsage = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentDate = new AtomicReference<>(LocalDate.now());

    /**
     * Checks if a request can proceed with LLM usage.
     * 
     * @param estimatedTokens Estimated tokens for this request
     * @return Decision with allowed action
     */
    public BudgetDecision checkBudget(int estimatedTokens) {
        resetIfNewDay();

        int currentUsage = dailyUsage.get();
        double usageRatio = (double) currentUsage / dailyBudget;

        // Check per-request limit
        if (estimatedTokens > perRequestBudget) {
            log.warn("[TokenGovernor] Request exceeds per-request budget: {} > {}",
                    estimatedTokens, perRequestBudget);
            return new BudgetDecision(
                    BudgetAction.DOWNGRADE,
                    currentUsage,
                    dailyBudget,
                    "Per-request budget exceeded");
        }

        // Check if would exceed daily limit
        if (currentUsage + estimatedTokens > dailyBudget) {
            log.warn("[TokenGovernor] Daily budget exhausted: {} + {} > {}",
                    currentUsage, estimatedTokens, dailyBudget);
            return new BudgetDecision(
                    BudgetAction.REJECT,
                    currentUsage,
                    dailyBudget,
                    "Daily budget exhausted");
        }

        // Check if above fallback threshold
        if (usageRatio >= fallbackThreshold) {
            log.info("[TokenGovernor] Above fallback threshold: {:.1f}% used", usageRatio * 100);
            return new BudgetDecision(
                    BudgetAction.FALLBACK_ONLY,
                    currentUsage,
                    dailyBudget,
                    "Using fallback to preserve budget");
        }

        // Check if above 50% (embeddings only mode)
        if (usageRatio >= 0.5) {
            return new BudgetDecision(
                    BudgetAction.EMBEDDINGS_ONLY,
                    currentUsage,
                    dailyBudget,
                    "Cached embeddings only");
        }

        return new BudgetDecision(
                BudgetAction.ALLOW,
                currentUsage,
                dailyBudget,
                "Within budget");
    }

    /**
     * Records actual token usage after an LLM call.
     * 
     * @param tokensUsed Actual tokens consumed
     */
    public void recordUsage(int tokensUsed) {
        resetIfNewDay();
        int newTotal = dailyUsage.addAndGet(tokensUsed);
        log.debug("[TokenGovernor] Recorded {} tokens, daily total: {}", tokensUsed, newTotal);
    }

    /**
     * Returns current usage statistics.
     */
    public UsageStats getStats() {
        resetIfNewDay();
        int current = dailyUsage.get();
        return new UsageStats(
                current,
                dailyBudget,
                (double) current / dailyBudget,
                dailyBudget - current,
                currentDate.get());
    }

    /**
     * Resets counter if it's a new day.
     */
    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        LocalDate stored = currentDate.get();

        if (!today.equals(stored)) {
            if (currentDate.compareAndSet(stored, today)) {
                int previousUsage = dailyUsage.getAndSet(0);
                log.info("[TokenGovernor] New day reset. Previous day usage: {}", previousUsage);
            }
        }
    }

    /**
     * Budget decision actions.
     */
    public enum BudgetAction {
        ALLOW, // Full LLM access
        EMBEDDINGS_ONLY, // Use cached embeddings, skip query gen LLM
        FALLBACK_ONLY, // Use deterministic fallback only
        DOWNGRADE, // Reduce token usage (shorter prompts)
        REJECT // No LLM calls allowed
    }

    /**
     * Budget decision record.
     */
    public record BudgetDecision(
            BudgetAction action,
            int currentUsage,
            int dailyBudget,
            String reason) {
        public boolean isAllowed() {
            return action == BudgetAction.ALLOW;
        }

        public boolean canUseLLM() {
            return action == BudgetAction.ALLOW || action == BudgetAction.EMBEDDINGS_ONLY;
        }
    }

    /**
     * Usage statistics record.
     */
    public record UsageStats(
            int tokensUsed,
            int dailyBudget,
            double usageRatio,
            int remainingBudget,
            LocalDate date) {
    }
}
