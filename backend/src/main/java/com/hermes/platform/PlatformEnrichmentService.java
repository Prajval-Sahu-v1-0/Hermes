package com.hermes.platform;

import com.hermes.feature.FeatureFlag;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for platform-specific enrichment services.
 * 
 * Each platform (Reddit, Instagram, Twitter, Twitch) implements this interface
 * to provide additional metadata for creator profiles.
 * 
 * All implementations are feature-gated and will return Optional.empty()
 * when the corresponding feature is not ENABLED.
 */
public interface PlatformEnrichmentService {

    /**
     * Returns the feature flag associated with this platform.
     */
    FeatureFlag getFeatureFlag();

    /**
     * Returns the platform identifier (e.g., "reddit", "instagram").
     */
    String getPlatformId();

    /**
     * Attempts to enrich a creator profile with platform-specific data.
     * 
     * @param creatorName The creator's name/username to search for
     * @return Platform enrichment data, or empty if not found/feature disabled
     */
    Optional<PlatformEnrichment> enrich(String creatorName);

    /**
     * Platform-agnostic enrichment data record.
     * Contains common fields across all platforms.
     */
    record PlatformEnrichment(
            String platformId,
            String platformUsername,
            String profileUrl,
            long followers,
            long engagement,
            Map<String, Object> platformSpecificData) {

        /**
         * Creates an empty enrichment (placeholder).
         */
        public static PlatformEnrichment empty(String platformId) {
            return new PlatformEnrichment(platformId, null, null, 0, 0, Map.of());
        }
    }
}
