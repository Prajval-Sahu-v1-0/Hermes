package com.hermes.platform;

import com.hermes.feature.FeatureFlag;
import com.hermes.feature.FeatureGuard;
import com.hermes.feature.FeatureRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Reddit enrichment service for creator profiles.
 * 
 * Adds subreddit presence and engagement data to creator profiles.
 * Feature-gated: only executes when REDDIT_ENRICHMENT is ENABLED.
 * 
 * Required credentials:
 * - hermes.reddit.client-id
 * - hermes.reddit.client-secret
 */
@Service
public class RedditEnrichmentService implements PlatformEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(RedditEnrichmentService.class);

    private final FeatureRegistry featureRegistry;
    private final String clientId;
    private final String clientSecret;

    public RedditEnrichmentService(
            FeatureRegistry featureRegistry,
            @Value("${hermes.reddit.client-id:}") String clientId,
            @Value("${hermes.reddit.client-secret:}") String clientSecret) {
        this.featureRegistry = featureRegistry;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public FeatureFlag getFeatureFlag() {
        return FeatureFlag.REDDIT_ENRICHMENT;
    }

    @Override
    public String getPlatformId() {
        return "reddit";
    }

    /**
     * Enriches a creator profile with Reddit data.
     * Guarded by REDDIT_ENRICHMENT feature flag.
     * 
     * @param creatorName The creator's name to search for on Reddit
     * @return Reddit enrichment data, or empty if disabled/not found
     */
    @Override
    @FeatureGuard(FeatureFlag.REDDIT_ENRICHMENT)
    public Optional<PlatformEnrichment> enrich(String creatorName) {
        // Feature is enabled at this point (guard passed)
        log.debug("[Reddit] Enriching creator: {}", creatorName);

        // TODO: Implement actual Reddit API call when credentials are available
        // For now, this is a placeholder that will be filled in when Reddit is enabled

        // This code path only executes when:
        // 1. hermes.reddit.client-id is set
        // 2. hermes.reddit.client-secret is set
        // 3. hermes.features.reddit.enrichment.enabled=true

        return Optional.empty(); // No mock data - real implementation pending
    }

    /**
     * Checks if Reddit credentials are configured (not necessarily enabled).
     */
    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
