package com.hermes.feature;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central registry for feature states.
 * Single source of truth for all feature enablement decisions.
 * 
 * Resolves feature states at startup based on:
 * 1. Credential availability (checked via property presence)
 * 2. Explicit feature flags
 * 
 * Thread-safe and immutable after initialization.
 */
@Service
public class FeatureRegistry {

    private static final Logger log = LoggerFactory.getLogger(FeatureRegistry.class);

    private final Environment environment;
    private final Map<FeatureFlag, FeatureState> featureStates;

    public FeatureRegistry(Environment environment) {
        this.environment = environment;
        this.featureStates = new EnumMap<>(FeatureFlag.class);
    }

    @PostConstruct
    public void initialize() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("                HERMES FEATURE REGISTRY                    ");
        log.info("═══════════════════════════════════════════════════════════");

        for (FeatureFlag flag : FeatureFlag.values()) {
            FeatureState state = resolveState(flag);
            featureStates.put(flag, state);
            logFeatureState(flag, state);
        }

        log.info("═══════════════════════════════════════════════════════════");
        log.info("Active features: {}", getEnabledFeatures());
        log.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Resolves the state for a feature flag.
     */
    private FeatureState resolveState(FeatureFlag flag) {
        // Always-enabled features (like YOUTUBE_CORE) are always ENABLED
        if (flag.isAlwaysEnabled()) {
            return FeatureState.ENABLED;
        }

        boolean hasCredentials = checkCredentials(flag);
        boolean flagEnabled = checkFlag(flag);

        return FeatureState.resolve(hasCredentials, flagEnabled);
    }

    /**
     * Checks if credentials are present for a feature.
     */
    private boolean checkCredentials(FeatureFlag flag) {
        return switch (flag) {
            case YOUTUBE_CORE -> true; // Always has credentials (required for startup)
            case REDDIT_ENRICHMENT -> hasProperty("hermes.reddit.client-id")
                    && hasProperty("hermes.reddit.client-secret");
            case INSTAGRAM_ENRICHMENT -> hasProperty("hermes.instagram.access-token");
            case TWITTER_ENRICHMENT -> hasProperty("hermes.twitter.bearer-token");
            case TWITCH_ENRICHMENT -> hasProperty("hermes.twitch.client-id")
                    && hasProperty("hermes.twitch.client-secret");
        };
    }

    /**
     * Checks if the feature flag is explicitly enabled.
     */
    private boolean checkFlag(FeatureFlag flag) {
        if (flag.isAlwaysEnabled()) {
            return true;
        }
        String propertyName = flag.getEnabledPropertyName();
        return environment.getProperty(propertyName, Boolean.class, false);
    }

    /**
     * Checks if a property exists and has a non-empty value.
     */
    private boolean hasProperty(String propertyName) {
        String value = environment.getProperty(propertyName);
        return value != null && !value.isBlank();
    }

    /**
     * Logs the feature state with appropriate formatting.
     */
    private void logFeatureState(FeatureFlag flag, FeatureState state) {
        String icon = switch (state) {
            case ENABLED -> "✓";
            case CONFIGURED -> "○";
            case DISABLED -> "✗";
        };
        String status = switch (state) {
            case ENABLED -> "ENABLED    ";
            case CONFIGURED -> "CONFIGURED ";
            case DISABLED -> "DISABLED   ";
        };
        log.info("  {} {} {}", icon, status, flag.name());
    }

    // ===== Public API =====

    /**
     * Returns the state of a feature.
     */
    public FeatureState getState(FeatureFlag flag) {
        return featureStates.getOrDefault(flag, FeatureState.DISABLED);
    }

    /**
     * Returns true if a feature is enabled and should execute.
     */
    public boolean isEnabled(FeatureFlag flag) {
        return getState(flag).isActive();
    }

    /**
     * Returns true if a feature has credentials (CONFIGURED or ENABLED).
     */
    public boolean isConfigured(FeatureFlag flag) {
        return getState(flag).hasCredentials();
    }

    /**
     * Returns all feature states as an immutable map.
     */
    public Map<FeatureFlag, FeatureState> getAllStates() {
        return Map.copyOf(featureStates);
    }

    /**
     * Returns a list of all enabled feature names.
     */
    public String getEnabledFeatures() {
        return featureStates.entrySet().stream()
                .filter(e -> e.getValue() == FeatureState.ENABLED)
                .map(e -> e.getKey().name())
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns feature status summary for observability.
     */
    public FeatureStatusSummary getStatusSummary() {
        int enabled = 0, configured = 0, disabled = 0;
        for (FeatureState state : featureStates.values()) {
            switch (state) {
                case ENABLED -> enabled++;
                case CONFIGURED -> configured++;
                case DISABLED -> disabled++;
            }
        }
        return new FeatureStatusSummary(enabled, configured, disabled, Map.copyOf(featureStates));
    }

    /**
     * Feature status summary record for API responses.
     */
    public record FeatureStatusSummary(
            int enabledCount,
            int configuredCount,
            int disabledCount,
            Map<FeatureFlag, FeatureState> features) {
    }
}
