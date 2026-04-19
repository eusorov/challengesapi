package com.challenges.api.dev;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("demo-seed")
@ConditionalOnProperty(
		prefix = "challenges.demo.seed",
		name = "on-startup",
		havingValue = "true",
		matchIfMissing = true)
@Order
public class DemoDataLoader implements ApplicationRunner {

	private final DemoDataSeedService seedService;

	public DemoDataLoader(DemoDataSeedService seedService) {
		this.seedService = seedService;
	}

	@Override
	public void run(ApplicationArguments args) {
		seedService.seedIfEmpty();
	}
}
