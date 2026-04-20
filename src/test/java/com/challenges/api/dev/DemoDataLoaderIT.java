package com.challenges.api.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk demo-seed integration test (1000 users / challenges). Excluded from the default {@code ./gradlew test}
 * run; enable with {@code ./gradlew test -PrunDemoSeedIT=true}.
 */
@SpringBootTest
@ActiveProfiles({"test", "demo-seed"})
@Transactional
class DemoDataLoaderIT {

	private final DemoDataSeedService seedService;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ParticipantRepository participants;
	private final CheckInRepository checkIns;

	@Autowired
	DemoDataLoaderIT(
			DemoDataSeedService seedService,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ParticipantRepository participants,
			CheckInRepository checkIns) {
		this.seedService = seedService;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.participants = participants;
		this.checkIns = checkIns;
	}

	@Test
	void demoSeedCreatesUsersAndChallenges() {
		seedService.seedIfEmpty();
		assertThat(users.existsByEmail(DemoDataSeedService.SEED_EMAIL_1)).isTrue();
		assertThat(users.count()).isEqualTo(DemoDataSeedService.BULK_USER_COUNT);
		assertThat(challenges.count()).isEqualTo(DemoDataSeedService.BULK_CHALLENGE_COUNT);
		int expectedPrivate =
				(DemoDataSeedService.BULK_CHALLENGE_COUNT - 1) / DemoDataSeedService.PRIVATE_CHALLENGE_INDEX_MOD
						+ 1;
		assertThat(challenges.countByIsPrivateTrue()).isEqualTo(expectedPrivate);
		assertThat(participants.count())
				.isEqualTo(
						(long) DemoDataSeedService.BULK_CHALLENGE_COUNT
								* DemoDataSeedService.PARTICIPANTS_PER_CHALLENGE);
		// 1 + (c % 5) subtasks per challenge → 200 × (1+2+3+4+5) = 3000
		assertThat(subTasks.count()).isEqualTo(3000L);
		assertThat(checkIns.count())
				.isEqualTo(
						(long) DemoDataSeedService.BULK_CHALLENGE_COUNT
								* DemoDataSeedService.PARTICIPANTS_PER_CHALLENGE
								* DemoDataSeedService.CHECKINS_PER_PARTICIPANT);
	}
}
