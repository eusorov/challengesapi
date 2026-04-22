package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
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
class InviteRepositoryTest {

	private final TestEntityManager entityManager;
	private final InviteRepository inviteRepository;

	@Autowired
	InviteRepositoryTest(TestEntityManager entityManager, InviteRepository inviteRepository) {
		this.entityManager = entityManager;
		this.inviteRepository = inviteRepository;
	}

	@Test
	void savesChallengeWideInvite() {
		User inviter = entityManager.persistAndFlush(User.forTest("inviter@example.com"));
		User invitee = entityManager.persistAndFlush(User.forTest("invitee@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(
				inviter, "Group run", null, LocalDate.of(2026, 7, 1), null, ChallengeCategory.OTHER, null, null, false));

		Invite inv = inviteRepository.save(new Invite(inviter, invitee, ch));
		entityManager.flush();
		entityManager.clear();

		Invite loaded = inviteRepository.findById(inv.getId()).orElseThrow();
		assertThat(loaded.getInviter().getId()).isEqualTo(inviter.getId());
		assertThat(loaded.getInvitee().getId()).isEqualTo(invitee.getId());
		assertThat(loaded.getChallenge().getId()).isEqualTo(ch.getId());
		assertThat(loaded.getSubTask()).isNull();
		assertThat(loaded.getStatus()).isEqualTo(InviteStatus.PENDING);
	}

	@Test
	void findsPendingByInvitee() {
		User inviter = entityManager.persistAndFlush(User.forTest("a@example.com"));
		User invitee = entityManager.persistAndFlush(User.forTest("b@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(
				inviter, "C", null, LocalDate.of(2026, 7, 2), null, ChallengeCategory.OTHER, null, null, false));
		SubTask st = entityManager.persistAndFlush(new SubTask(ch, "mile", 0));
		inviteRepository.save(new Invite(inviter, invitee, ch, st));
		entityManager.flush();
		entityManager.clear();

		assertThat(inviteRepository.findByInvitee_IdAndStatus(invitee.getId(), InviteStatus.PENDING)).hasSize(1);
	}

	@Test
	void rejectsSelfInviteWhenIdsPresent() {
		User u = entityManager.persistAndFlush(User.forTest("solo@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(
				u, "Solo", null, LocalDate.of(2026, 7, 3), null, ChallengeCategory.OTHER, null, null, false));

		Throwable thrown = catchThrowable(() -> entityManager.persistAndFlush(new Invite(u, u, ch)));
		assertThat(thrown).isNotNull();
		assertThat(throwableChainHasInstance(thrown, IllegalStateException.class)).isTrue();
	}

	private static boolean throwableChainHasInstance(Throwable t, Class<? extends Throwable> type) {
		for (Throwable cur = t; cur != null; cur = cur.getCause()) {
			if (type.isInstance(cur)) {
				return true;
			}
		}
		return false;
	}
}
