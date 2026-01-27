package com.hermes.feature;

/**
 * Enumeration of all feature flags in the Hermes platform.
 * 
 * YOUTUBE_CORE is always enabled (the core platform).
 * Other features are gated and require both credentials + flag=true to
 * activate.
 */
public enum FeatureFlag {

    /**
     * Core YouTube search and discovery.
     * Always enabled - this is the base platform.
     */
    YOUTUBE_CORE("youtube.core", true),

    /**
     * Reddit enrichment for creator profiles.
     * Adds subreddit presence and engagement data.
     */
    REDDIT_ENRICHMENT("reddit.enrichment", false),

    /**
     * Instagram enrichment for creator profiles.
     * Adds Instagram metrics and cross-platform presence.
     */
    INSTAGRAM_ENRICHMENT("instagram.enrichment", false),

    /**
     * Twitter/X enrichment for creator profiles.
     * Adds Twitter metrics and social engagement data.
     */
    TWITTER_ENRICHMENT("twitter.enrichment", false),

    /**
     * Twitch enrichment for creator profiles.
     * Adds live streaming metrics and presence.
     */
    TWITCH_ENRICHMENT("twitch.enrichment", false);

    private final String configKey;
    private final boolean alwaysEnabled;

    FeatureFlag(String configKey, boolean alwaysEnabled) {
        this.configKey = configKey;
        this.alwaysEnabled = alwaysEnabled;
    }

    /**
     * Returns the configuration property key for this feature.
     * Used to lookup: hermes.features.{configKey}.enabled
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Returns true if this feature is always enabled (cannot be disabled).
     */
    public boolean isAlwaysEnabled() {
        return alwaysEnabled;
    }

    /**
     * Returns the property name for the enabled flag.
     * Example: hermes.features.reddit.enrichment.enabled
     */
    public String getEnabledPropertyName() {
        return "hermes.features." + configKey + ".enabled";
    }
}
