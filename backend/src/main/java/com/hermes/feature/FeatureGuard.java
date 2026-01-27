package com.hermes.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to guard methods based on feature flag state.
 * 
 * When applied to a method, the FeatureGuardAspect will:
 * - Check if the specified feature is ENABLED
 * - If not enabled, return null/empty without executing
 * - Log the skip reason once per feature
 * 
 * Usage:
 * {@code
 * @FeatureGuard(FeatureFlag.REDDIT_ENRICHMENT)
 * public Optional<RedditData> enrichWithReddit(String creatorId) {
 * // This only executes if REDDIT_ENRICHMENT is ENABLED
 * }
 * }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureGuard {

    /**
     * The feature flag that must be ENABLED for this method to execute.
     */
    FeatureFlag value();

    /**
     * If true, logs a message when the method is skipped.
     * Default is false to avoid log spam.
     */
    boolean logSkip() default false;
}
