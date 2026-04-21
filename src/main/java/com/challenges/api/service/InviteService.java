package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import com.challenges.api.model.Participant;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.InviteRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.InviteRequest;
import com.challenges.api.web.dto.InviteUpdateRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class InviteService {

	private final InviteRepository invites;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ParticipantRepository participants;

	public InviteService(
			InviteRepository invites,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ParticipantRepository participants) {
		this.invites = invites;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.participants = participants;
	}

	@Transactional(readOnly = true)
	public @NonNull Page<Invite> list(@Nullable Long challengeIdFilter, @NonNull Pageable pageable) {
		Assert.notNull(pageable, "pageable must not be null");
		Page<Long> idPage =
				challengeIdFilter != null
						? invites.findIdsForChallengeOrderByIdAsc(challengeIdFilter, pageable)
						: invites.findIdsOrderByIdAsc(pageable);
		if (idPage.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
		}
		return new PageImpl<>(
				invites.findByIdInWithAssociations(idPage.getContent()), pageable, idPage.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Optional<Invite> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return invites.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<Invite> create(@NonNull InviteRequest req) {
		Assert.notNull(req, "request must not be null");
		var inviter = users.findById(req.inviterUserId());
		var invitee = users.findById(req.inviteeUserId());
		var challenge = challenges.findById(req.challengeId());
		if (inviter.isEmpty() || invitee.isEmpty() || challenge.isEmpty()) {
			return Optional.empty();
		}
		SubTask st = null;
		if (req.subTaskId() != null) {
			var ost = subTasks.findById(req.subTaskId());
			if (ost.isEmpty()) {
				return Optional.empty();
			}
			st = ost.get();
			if (!st.getChallenge().getId().equals(challenge.get().getId())) {
				throw new IllegalStateException("subTask must belong to the challenge");
			}
		}
		Invite inv = new Invite(inviter.get(), invitee.get(), challenge.get(), st);
		if (req.status() != null) {
			inv.setStatus(req.status());
		}
		if (req.expiresAt() != null) {
			inv.setExpiresAt(req.expiresAt());
		}
		Invite saved = invites.save(inv);
		ensureParticipantForAcceptedInvite(saved);
		return Optional.of(saved);
	}

	@Transactional
	public Optional<Invite> update(@NonNull Long id, @NonNull InviteUpdateRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return invites.findByIdWithAssociations(id).map(inv -> {
			if (req.status() != null) {
				inv.setStatus(req.status());
			}
			if (req.expiresAt() != null) {
				inv.setExpiresAt(req.expiresAt());
			}
			Invite saved = invites.save(inv);
			ensureParticipantForAcceptedInvite(saved);
			return saved;
		});
	}

	private void ensureParticipantForAcceptedInvite(Invite inv) {
		syncParticipantForAcceptedInvite(inv);
	}

	/**
	 * @return {@code true} if a new {@link Participant} row was saved
	 */
	private boolean syncParticipantForAcceptedInvite(Invite inv) {
		if (inv.getStatus() != InviteStatus.ACCEPTED) {
			return false;
		}
		User invitee = inv.getInvitee();
		Challenge challenge = inv.getChallenge();
		SubTask st = inv.getSubTask();
		if (st == null) {
			if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(invitee.getId(), challenge.getId())) {
				participants.save(new Participant(invitee, challenge));
				return true;
			}
			return false;
		}
		if (!participants.existsByUser_IdAndChallenge_IdAndSubTask_Id(invitee.getId(), challenge.getId(), st.getId())) {
			participants.save(new Participant(invitee, challenge, st));
			return true;
		}
		return false;
	}

	/**
	 * Accepts the oldest usable {@link InviteStatus#PENDING} invite for (invitee, challenge),
	 * syncs participant, returns the loaded invite with associations and whether a new participant row was inserted.
	 */
	@Transactional
	public Optional<InviteJoinAcceptResult> acceptOldestUsablePendingInviteForJoin(
			@NonNull Long inviteeUserId, @NonNull Long challengeId) {
		Assert.notNull(inviteeUserId, "inviteeUserId must not be null");
		Assert.notNull(challengeId, "challengeId must not be null");
		Instant now = Instant.now();
		List<Invite> pending =
				invites.findByInvitee_IdAndChallenge_IdAndStatusOrderByIdAsc(
						inviteeUserId, challengeId, InviteStatus.PENDING);
		for (Invite inv : pending) {
			if (inv.getExpiresAt() != null && !inv.getExpiresAt().isAfter(now)) {
				continue;
			}
			inv.setStatus(InviteStatus.ACCEPTED);
			Invite saved = invites.save(inv);
			boolean inserted = syncParticipantForAcceptedInvite(saved);
			Invite withAssoc = invites.findByIdWithAssociations(saved.getId()).orElseThrow();
			return Optional.of(new InviteJoinAcceptResult(withAssoc, inserted));
		}
		return Optional.empty();
	}

	@Transactional
	public boolean delete(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		if (!invites.existsById(id)) {
			return false;
		}
		invites.deleteById(id);
		return true;
	}
}
