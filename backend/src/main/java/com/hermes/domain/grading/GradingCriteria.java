package com.hermes.domain.grading;

/**
 * User filter preferences for grading creators.
 * Represents the criteria for matching creators to user intent.
 */
public record GradingCriteria(
        String baseGenre,
        AudienceScale audienceScale,
        EngagementQuality engagementQuality,
        ActivityLevel activityLevel,
        String location) {
    /**
     * Audience size preference.
     */
    public enum AudienceScale {
        SMALL(0, 10_000),
        MEDIUM(10_000, 100_000),
        LARGE(100_000, Long.MAX_VALUE);

        private final long minSubscribers;
        private final long maxSubscribers;

        AudienceScale(long min, long max) {
            this.minSubscribers = min;
            this.maxSubscribers = max;
        }

        public long getMinSubscribers() {
            return minSubscribers;
        }

        public long getMaxSubscribers() {
            return maxSubscribers;
        }

        public boolean matches(long subscriberCount) {
            return subscriberCount >= minSubscribers && subscriberCount < maxSubscribers;
        }
    }

    /**
     * Engagement quality preference.
     */
    public enum EngagementQuality {
        LOW(0.0, 50.0),
        MEDIUM(50.0, 200.0),
        HIGH(200.0, Double.MAX_VALUE);

        private final double minRatio;
        private final double maxRatio;

        EngagementQuality(double min, double max) {
            this.minRatio = min;
            this.maxRatio = max;
        }

        public double getMinRatio() {
            return minRatio;
        }

        public double getMaxRatio() {
            return maxRatio;
        }
    }

    /**
     * Activity level preference (uploads per month).
     */
    public enum ActivityLevel {
        OCCASIONAL(0.0, 2.0),
        CONSISTENT(2.0, 8.0),
        VERY_ACTIVE(8.0, Double.MAX_VALUE);

        private final double minUploadsPerMonth;
        private final double maxUploadsPerMonth;

        ActivityLevel(double min, double max) {
            this.minUploadsPerMonth = min;
            this.maxUploadsPerMonth = max;
        }

        public double getMinUploadsPerMonth() {
            return minUploadsPerMonth;
        }

        public double getMaxUploadsPerMonth() {
            return maxUploadsPerMonth;
        }
    }

    /**
     * Creates criteria with defaults for unspecified filters.
     */
    public static GradingCriteria withDefaults(String genre) {
        return new GradingCriteria(
                genre,
                AudienceScale.MEDIUM,
                EngagementQuality.MEDIUM,
                ActivityLevel.CONSISTENT,
                null);
    }
}
