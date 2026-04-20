package com.challenges.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "challenges.check-ins")
public record CheckInRetentionProperties(
		@DefaultValue("90") int retentionDaysAfterChallengeEnd,
		@DefaultValue("true") boolean rollupEnabled,
		@DefaultValue("3600000") long rollupFixedDelayMs,
		@DefaultValue("10") int rollupBatchSize) {
}
