package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Invite;
import com.challenges.api.model.Participant;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class ParticipantService {

	private final ParticipantRepository participants;
	private final ChallengeRepository challenges;
	private final UserRepository users;
	private final InviteService inviteService;
	private final ChallengeService challengeService;

	public ParticipantService(
			ParticipantRepository participants,
			ChallengeRepository challenges,
			UserRepository users,
			InviteService inviteService,
			ChallengeService challengeService) {
		this.participants = participants;
		this.challenges = challenges;
		this.users = users;
		this.inviteService = inviteService;
		this.challengeService = challengeService;
	}

	/**
	 * Paged participants if the challenge exists and the viewer may see it (same visibility as {@code GET
	 * /api/challenges/{id}}). Otherwise {@link HttpStatus#NOT_FOUND}.
	 */
	@Transactional(readOnly = true)
	public @NonNull Page<Participant> listForChallengeIfVisible(
			@NonNull Long challengeId, @Nullable Long viewerUserId, @NonNull Pageable pageable) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(pageable, "pageable must not be null");
		if (challengeService.findByIdForViewer(challengeId, viewerUserId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return listForChallenge(challengeId, pageable);
	}

	@Transactional(readOnly = true)
	public @NonNull Page<Participant> listForChallenge(@NonNull Long challengeId, @NonNull Pageable pageable) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(pageable, "pageable must not be null");
		Page<Long> idPage = participants.findIdsForChallengeOrderByIdAsc(challengeId, pageable);
		if (idPage.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
		}
		return new PageImpl<>(
				participants.findByIdInWithAssociations(idPage.getContent()), pageable, idPage.getTotalElements());
	}

	@Transactional
	public @NonNull JoinChallengeOutcome joinChallenge(@NonNull Long challengeId, @NonNull Long userId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(userId, "userId must not be null");
		Challenge challenge =
				challenges
						.findByIdWithSubtasksAndOwner(challengeId)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		User user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (challenge.getOwner().getId().equals(userId) || !challenge.isPrivate()) {
			return ensureChallengeWideParticipant(user, challenge);
		}
		Optional<InviteJoinAcceptResult> result = inviteService.acceptOldestUsablePendingInviteForJoin(userId, challengeId);
		if (result.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
		InviteJoinAcceptResult accepted = result.get();
		Invite invite = accepted.invite();
		boolean created = accepted.participantInserted();
		Participant p =
				invite.getSubTask() == null
						? participants.findChallengeWideWithAssociations(userId, challengeId).orElseThrow()
						: participants
								.findSubTaskScopedWithAssociations(userId, challengeId, invite.getSubTask().getId())
								.orElseThrow();
		return new JoinChallengeOutcome(p, created);
	}

	private JoinChallengeOutcome ensureChallengeWideParticipant(User user, Challenge challenge) {
		Long uid = user.getId();
		Long cid = challenge.getId();
		if (participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(uid, cid)) {
			Participant p = participants.findChallengeWideWithAssociations(uid, cid).orElseThrow();
			return new JoinChallengeOutcome(p, false);
		}
		participants.save(new Participant(user, challenge));
		Participant p = participants.findChallengeWideWithAssociations(uid, cid).orElseThrow();
		return new JoinChallengeOutcome(p, true);
	}
}
