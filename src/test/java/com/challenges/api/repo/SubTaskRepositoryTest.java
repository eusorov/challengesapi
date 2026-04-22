package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
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
class SubTaskRepositoryTest {

	private final TestEntityManager entityManager;
	private final SubTaskRepository subTaskRepository;

	@Autowired
	SubTaskRepositoryTest(TestEntityManager entityManager, SubTaskRepository subTaskRepository) {
		this.entityManager = entityManager;
		this.subTaskRepository = subTaskRepository;
	}

	@Test
	void persistsSubTaskLinkedToChallenge() {
		User u = entityManager.persistAndFlush(User.forTest("st-owner@example.com"));
		Challenge ch = new Challenge(u, "Main", null, LocalDate.of(2026, 3, 1), null, ChallengeCategory.OTHER, null, null, false);
		entityManager.persistAndFlush(ch);
		SubTask st = new SubTask(ch, "First sub", 0);
		subTaskRepository.save(st);
		entityManager.flush();
		entityManager.clear();

		SubTask loaded = subTaskRepository.findById(st.getId()).orElseThrow();
		assertThat(loaded.getChallenge().getId()).isEqualTo(ch.getId());
	}
}
