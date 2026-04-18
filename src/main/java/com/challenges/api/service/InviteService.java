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
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
	public @NonNull List<Invite> list(@Nullable Long challengeIdFilter) {
		if (challengeIdFilter != null) {
			return invites.findByChallenge_Id(challengeIdFilter);
		}
		return invites.findAllWithAssociations();
	}

	@Transactional(readOnly = true)
	public Optional<Invite> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return invites.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<Invite> create(InviteRequest req) {
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
	public Optional<Invite> update(Long id, InviteUpdateRequest req) {
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
		if (inv.getStatus() != InviteStatus.ACCEPTED) {
			return;
		}
		User invitee = inv.getInvitee();
		Challenge challenge = inv.getChallenge();
		SubTask st = inv.getSubTask();
		if (st == null) {
			if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(invitee.getId(), challenge.getId())) {
				participants.save(new Participant(invitee, challenge));
			}
		} else {
			if (!participants.existsByUser_IdAndChallenge_IdAndSubTask_Id(invitee.getId(), challenge.getId(), st.getId())) {
				participants.save(new Participant(invitee, challenge, st));
			}
		}
	}

	@Transactional
	public boolean delete(Long id) {
		Assert.notNull(id, "id must not be null");
		if (!invites.existsById(id)) {
			return false;
		}
		invites.deleteById(id);
		return true;
	}
}
