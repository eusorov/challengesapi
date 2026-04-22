package com.challenges.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.CheckIn;
import com.challenges.api.model.RollupStatus;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.CheckInRollupRunRepository;
import com.challenges.api.repo.CheckInSummaryRepository;
import com.challenges.api.repo.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CheckInRollupServiceIT {

	@Autowired
	private UserRepository users;

	@Autowired
	private ChallengeRepository challenges;

	@Autowired
	private CheckInRepository checkIns;

	@Autowired
	private CheckInSummaryRepository summaries;

	@Autowired
	private CheckInRollupRunRepository rollupRuns;

	@Autowired
	private CheckInRollupService rollupService;

	@Test
	void rollupChallenge_movesRowsToSummariesAndDeletesCheckIns() {
		User u = users.save(User.forTest("rollup-user@example.com"));
		LocalDate end = LocalDate.now(ZoneId.systemDefault()).minusDays(100);
		Challenge ch = challenges.save(new Challenge(u, "Past", null, end.minusMonths(1), end, ChallengeCategory.PRODUCTIVITY));
		checkIns.save(new CheckIn(u, ch, end.minusDays(1), null));

		assertThat(checkIns.countByChallenge_Id(ch.getId())).isEqualTo(1);
		assertThat(summaries.countByChallenge_Id(ch.getId())).isZero();

		rollupService.rollupChallenge(ch.getId());

		assertThat(checkIns.countByChallenge_Id(ch.getId())).isZero();
		assertThat(summaries.countByChallenge_Id(ch.getId())).isEqualTo(1);
		var run = rollupRuns.findById(ch.getId()).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(RollupStatus.COMPLETE);
	}

	@Test
	void rollupChallenge_idempotentWhenAlreadyComplete() {
		User u = users.save(User.forTest("rollup-idem@example.com"));
		LocalDate end = LocalDate.now(ZoneId.systemDefault()).minusDays(100);
		Challenge ch = challenges.save(new Challenge(u, "Past2", null, end.minusMonths(1), end, ChallengeCategory.PRODUCTIVITY));
		checkIns.save(new CheckIn(u, ch, end.minusDays(2), null));

		rollupService.rollupChallenge(ch.getId());
		rollupService.rollupChallenge(ch.getId());

		assertThat(summaries.countByChallenge_Id(ch.getId())).isEqualTo(1);
	}
}
