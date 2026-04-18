package com.challenges.api.service;

import com.challenges.api.model.Invite;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.InviteRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.InviteRequest;
import com.challenges.api.web.dto.InviteUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class InviteService {

	private final InviteRepository invites;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public InviteService(
			InviteRepository invites,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks) {
		this.invites = invites;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@Transactional(readOnly = true)
	public List<Invite> list(Long challengeIdFilter) {
		if (challengeIdFilter != null) {
			return invites.findByChallenge_Id(challengeIdFilter);
		}
		return invites.findAll();
	}

	@Transactional(readOnly = true)
	public Optional<Invite> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return invites.findById(id);
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
		return Optional.of(invites.save(inv));
	}

	@Transactional
	public Optional<Invite> update(Long id, InviteUpdateRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return invites.findById(id).map(inv -> {
			if (req.status() != null) {
				inv.setStatus(req.status());
			}
			if (req.expiresAt() != null) {
				inv.setExpiresAt(req.expiresAt());
			}
			return invites.save(inv);
		});
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
