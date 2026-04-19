package com.challenges.api.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"test", "demo-seed"})
@Transactional
class DemoDataLoaderIT {

	private final DemoDataSeedService seedService;
	private final UserRepository users;
	private final ChallengeRepository challenges;

	@Autowired
	DemoDataLoaderIT(
			DemoDataSeedService seedService, UserRepository users, ChallengeRepository challenges) {
		this.seedService = seedService;
		this.users = users;
		this.challenges = challenges;
	}

	@Test
	void demoSeedCreatesUsersAndChallenges() {
		seedService.seedIfEmpty();
		assertThat(users.existsByEmail(DemoDataSeedService.SEED_EMAIL_1)).isTrue();
		assertThat(users.existsByEmail("seed10@demo.local")).isTrue();
		assertThat(challenges.findAll()).hasSizeGreaterThanOrEqualTo(10);
	}
}
