package com.hermes.feature;

/**
 * Three-state model for feature enablement.
 * 
 * A feature progresses through states based on:
 * 1. Credential availability
 * 2. Explicit enablement flag
 */
public enum FeatureState {

    /**
     * Feature is disabled.
     * Either: no credentials present, OR flag explicitly set to false.
     * 
     * Behavior:
     * - No execution
     * - No UI exposure
     * - No background jobs
     * - Silent skip in services
     */
    DISABLED,

    /**
     * Feature is configured but not enabled.
     * Credentials are present, but flag is false.
     * 
     * Behavior:
     * - Ready for activation
     * - Still hidden and non-executing
     * - No background jobs
     * - Can be enabled without code changes
     */
    CONFIGURED,

    /**
     * Feature is fully enabled.
     * Credentials present AND flag is true.
     * 
     * Behavior:
     * - Full execution
     * - Exposed in UI/API
     * - Background jobs active
     * - Included in rankings
     */
    ENABLED;

    /**
     * Returns true if the feature should execute.
     * Only ENABLED state allows execution.
     */
    public boolean isActive() {
        return this == ENABLED;
    }

    /**
     * Returns true if credentials are available.
     * Both CONFIGURED and ENABLED have credentials.
     */
    public boolean hasCredentials() {
        return this == CONFIGURED || this == ENABLED;
    }

    /**
     * Resolves feature state from credentials and flag.
     * 
     * @param hasCredentials Whether valid credentials are present
     * @param flagEnabled    Whether the feature flag is explicitly enabled
     * @return The resolved feature state
     */
    public static FeatureState resolve(boolean hasCredentials, boolean flagEnabled) {
        if (!hasCredentials) {
            return DISABLED;
        }
        return flagEnabled ? ENABLED : CONFIGURED;
    }
}
