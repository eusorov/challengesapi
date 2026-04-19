package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Participant;
import com.challenges.api.model.SubTask;
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
class ParticipantRepositoryTest {

	private final TestEntityManager entityManager;
	private final ParticipantRepository participantRepository;

	@Autowired
	ParticipantRepositoryTest(TestEntityManager entityManager, ParticipantRepository participantRepository) {
		this.entityManager = entityManager;
		this.participantRepository = participantRepository;
	}

	@Test
	void findsWideVsSubtaskScopedParticipants() {
		User u1 = entityManager.persistAndFlush(User.forTest("p1@example.com"));
		User u2 = entityManager.persistAndFlush(User.forTest("p2@example.com"));
		Challenge ch =
				entityManager.persistAndFlush(new Challenge(u1, "P challenge", null, LocalDate.of(2026, 4, 1), null));
		SubTask st = entityManager.persistAndFlush(new SubTask(ch, "Scoped sub", 0));

		Participant wide = participantRepository.save(new Participant(u1, ch));
		Participant scoped = participantRepository.save(new Participant(u2, ch, st));
		entityManager.flush();
		entityManager.clear();

		var idPage = participantRepository.findIdsForChallengeOrderByIdAsc(ch.getId(), PageRequest.of(0, 50));
		List<Participant> byChallenge = participantRepository.findByIdInWithAssociations(idPage.getContent());
		assertThat(byChallenge).extracting(Participant::getId).containsExactlyInAnyOrder(wide.getId(), scoped.getId());

		List<Participant> wideOnly = participantRepository.findByChallenge_IdAndSubTaskIsNull(ch.getId());
		assertThat(wideOnly).singleElement().satisfies(p -> assertThat(p.getId()).isEqualTo(wide.getId()));

		List<Participant> bySub = participantRepository.findBySubTask_Id(st.getId());
		assertThat(bySub).singleElement().satisfies(p -> assertThat(p.getId()).isEqualTo(scoped.getId()));
	}
}
