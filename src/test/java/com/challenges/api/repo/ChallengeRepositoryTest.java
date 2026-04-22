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
		Challenge ch = new Challenge(u, "Jan 2026", null, start, end, ChallengeCategory.PRODUCTIVITY, null, null, false);
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
		Challenge ch = new Challenge(u, "Open", "no end", start, null, ChallengeCategory.SLEEP, null, null, false);
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
		Challenge pub = new Challenge(u, "public ch", null, d, null, ChallengeCategory.OTHER, null, null, false);
		Challenge priv = new Challenge(u, "private ch", null, d, null, ChallengeCategory.OTHER, null, null, true);
		challengeRepository.saveAll(List.of(pub, priv));
		entityManager.flush();
		entityManager.clear();

		var page = challengeRepository.findNonPrivateIdsOrderByIdAsc(PageRequest.of(0, 20));
		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent()).containsExactly(pub.getId());
	}

	@Test
	void findNonPrivateIdsWithFilters_bySearchCategoryAndCity() {
		User u = entityManager.persistAndFlush(User.forTest("owner-filter@example.com"));
		LocalDate d = LocalDate.of(2026, 4, 1);
		Challenge a =
				new Challenge(u, "Morning run club", "weekly runs", d, null, ChallengeCategory.HEALTH_AND_FITNESS, null, null, false);
		a.setCity("Berlin");
		Challenge b = new Challenge(u, "Evening walk", null, d, null, ChallengeCategory.PRODUCTIVITY, null, null, false);
		b.setCity("Paris");
		challengeRepository.saveAll(List.of(a, b));
		entityManager.flush();
		entityManager.clear();

		var byQ =
				challengeRepository.findNonPrivateIdsWithFilters(
						"%morning%", null, null, PageRequest.of(0, 20));
		assertThat(byQ.getContent()).containsExactly(a.getId());

		var byCat =
				challengeRepository.findNonPrivateIdsWithFilters(
						null, ChallengeCategory.PRODUCTIVITY.name(), null, PageRequest.of(0, 20));
		assertThat(byCat.getContent()).containsExactly(b.getId());

		var byCity =
				challengeRepository.findNonPrivateIdsWithFilters(
						null, null, "berlin", PageRequest.of(0, 20));
		assertThat(byCity.getContent()).containsExactly(a.getId());
	}

	@Test
	void findIdsByOwnerUserIdOrderByIdAsc_returnsOnlyThatOwnersChallengesOrderedById() {
		User u1 = entityManager.persistAndFlush(User.forTest("repo-owner-a@example.com"));
		User u2 = entityManager.persistAndFlush(User.forTest("repo-owner-b@example.com"));
		LocalDate d = LocalDate.of(2026, 5, 1);
		Challenge c1 = new Challenge(u1, "a1", null, d, null, ChallengeCategory.OTHER, null, null, false);
		Challenge c2 = new Challenge(u1, "a2", null, d, null, ChallengeCategory.OTHER, null, null, true);
		Challenge c3 = new Challenge(u2, "b1", null, d, null, ChallengeCategory.OTHER, null, null, false);
		challengeRepository.saveAll(List.of(c1, c2, c3));
		entityManager.flush();
		entityManager.clear();

		var page = challengeRepository.findIdsByOwnerUserIdOrderByIdAsc(u1.getId(), PageRequest.of(0, 20));
		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).containsExactly(c1.getId(), c2.getId());
	}
}
