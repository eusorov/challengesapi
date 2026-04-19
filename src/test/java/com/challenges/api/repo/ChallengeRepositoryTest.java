package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChallengeRepositoryTest {

	private final TestEntityManager entityManager;
	private final ChallengeRepository challengeRepository;

	@Autowired
	ChallengeRepositoryTest(TestEntityManager entityManager, ChallengeRepository challengeRepository) {
		this.entityManager = entityManager;
		this.challengeRepository = challengeRepository;
	}

	@Test
	void persistsBoundedChallengeWithStartAndEnd() {
		User u = entityManager.persistAndFlush(User.forTest("owner-bounded@example.com"));
		LocalDate start = LocalDate.of(2026, 1, 1);
		LocalDate end = LocalDate.of(2026, 1, 31);
		Challenge ch = new Challenge(u, "Jan 2026", null, start, end);
		challengeRepository.save(ch);
		entityManager.flush();
		entityManager.clear();

		Challenge loaded = challengeRepository.findById(ch.getId()).orElseThrow();
		assertThat(loaded.getStartDate()).isEqualTo(start);
		assertThat(loaded.getEndDate()).isEqualTo(end);
	}

	@Test
	void persistsOpenEndedChallengeWithNullEndDate() {
		User u = entityManager.persistAndFlush(User.forTest("owner-open@example.com"));
		LocalDate start = LocalDate.of(2026, 2, 1);
		Challenge ch = new Challenge(u, "Open", "no end", start, null);
		challengeRepository.save(ch);
		entityManager.flush();
		entityManager.clear();

		Challenge loaded = challengeRepository.findById(ch.getId()).orElseThrow();
		assertThat(loaded.getStartDate()).isEqualTo(start);
		assertThat(loaded.getEndDate()).isNull();
	}
}
