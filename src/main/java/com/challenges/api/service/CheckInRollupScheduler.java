package com.challenges.api.service;

import com.challenges.api.config.CheckInRetentionProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CheckInRollupScheduler {

	private final CheckInRollupService rollupService;
	private final CheckInRetentionProperties retentionProps;

	public CheckInRollupScheduler(CheckInRollupService rollupService, CheckInRetentionProperties retentionProps) {
		this.rollupService = rollupService;
		this.retentionProps = retentionProps;
	}

	@Scheduled(fixedDelayString = "${challenges.check-ins.rollup-fixed-delay-ms:3600000}")
	public void scheduledRollup() {
		if (!retentionProps.rollupEnabled()) {
			return;
		}
		rollupService.runBatch();
	}
}
