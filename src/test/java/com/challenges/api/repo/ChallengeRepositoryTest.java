package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.User;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

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
		Challenge ch = new Challenge(u, "Jan 2026", null, start, end, ChallengeCategory.PRODUCTIVITY);
		challengeRepository.save(ch);
		entityManager.flush();
		entityManager.clear();

		Challenge loaded = challengeRepository.findById(ch.getId()).orElseThrow();
		assertThat(loaded.getStartDate()).isEqualTo(start);
		assertThat(loaded.getEndDate()).isEqualTo(end);
		assertThat(loaded.getCategory()).isEqualTo(ChallengeCategory.PRODUCTIVITY);
		assertThat(loaded.isPrivate()).isFalse();
	}

	@Test
	void persistsOpenEndedChallengeWithNullEndDate() {
		User u = entityManager.persistAndFlush(User.forTest("owner-open@example.com"));
		LocalDate start = LocalDate.of(2026, 2, 1);
		Challenge ch = new Challenge(u, "Open", "no end", start, null, ChallengeCategory.SLEEP);
		challengeRepository.save(ch);
		entityManager.flush();
		entityManager.clear();

		Challenge loaded = challengeRepository.findById(ch.getId()).orElseThrow();
		assertThat(loaded.getStartDate()).isEqualTo(start);
		assertThat(loaded.getEndDate()).isNull();
		assertThat(loaded.getCategory()).isEqualTo(ChallengeCategory.SLEEP);
		assertThat(loaded.isPrivate()).isFalse();
	}

	@Test
	void findNonPrivateIdsOrderByIdAsc_excludesPrivateChallenges() {
		User u = entityManager.persistAndFlush(User.forTest("owner-mix@example.com"));
		LocalDate d = LocalDate.of(2026, 3, 1);
		Challenge pub = new Challenge(u, "public ch", null, d, null, ChallengeCategory.OTHER, false);
		Challenge priv = new Challenge(u, "private ch", null, d, null, ChallengeCategory.OTHER, true);
		challengeRepository.saveAll(List.of(pub, priv));
		entityManager.flush();
		entityManager.clear();

		var page = challengeRepository.findNonPrivateIdsOrderByIdAsc(PageRequest.of(0, 20));
		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent()).containsExactly(pub.getId());
	}
}
