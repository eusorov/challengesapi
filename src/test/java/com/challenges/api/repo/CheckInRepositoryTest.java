package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.CheckIn;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CheckInRepositoryTest {

	private final TestEntityManager entityManager;
	private final CheckInRepository checkInRepository;

	@Autowired
	CheckInRepositoryTest(TestEntityManager entityManager, CheckInRepository checkInRepository) {
		this.entityManager = entityManager;
		this.checkInRepository = checkInRepository;
	}

	@Test
	void persistsChallengeLevelAndSubtaskLevelCheckIns() {
		User u = entityManager.persistAndFlush(User.forTest("checkin@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(
				u, "CI", null, LocalDate.of(2026, 6, 1), null, ChallengeCategory.OTHER, null, null, false));
		SubTask st = entityManager.persistAndFlush(new SubTask(ch, "Part", 0));
		LocalDate day = LocalDate.of(2026, 6, 15);

		CheckIn wide = checkInRepository.save(new CheckIn(u, ch, day, null));
		CheckIn scoped = checkInRepository.save(new CheckIn(u, ch, day, st));
		entityManager.flush();
		entityManager.clear();

		CheckIn loadedWide = checkInRepository.findById(wide.getId()).orElseThrow();
		assertThat(loadedWide.getSubTask()).isNull();

		CheckIn loadedScoped = checkInRepository.findById(scoped.getId()).orElseThrow();
		assertThat(loadedScoped.getChallenge().getId()).isEqualTo(ch.getId());
		assertThat(loadedScoped.getSubTask()).isNotNull();
		if (loadedScoped.getSubTask() != null) {
			assertThat(loadedScoped.getSubTask().getId()).isEqualTo(st.getId());
		}
	}
}
