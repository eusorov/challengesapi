package com.challenges.api.service;

import com.challenges.api.model.CheckIn;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.CheckInRequest;
import com.challenges.api.web.dto.CheckInUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class CheckInService {

	private final CheckInRepository checkIns;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public CheckInService(
			CheckInRepository checkIns,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks) {
		this.checkIns = checkIns;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@Transactional(readOnly = true)
	public @NonNull List<CheckIn> listForChallenge(Long challengeId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		return checkIns.findByChallenge_IdOrderByCheckDateDesc(challengeId);
	}

	@Transactional(readOnly = true)
	public Optional<CheckIn> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return checkIns.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<CheckIn> create(CheckInRequest req) {
		Assert.notNull(req, "request must not be null");
		var user = users.findById(req.userId());
		var challenge = challenges.findById(req.challengeId());
		if (user.isEmpty() || challenge.isEmpty()) {
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
				throw new IllegalStateException("subTask must belong to the check-in challenge");
			}
		}
		return Optional.of(checkIns.save(new CheckIn(user.get(), challenge.get(), req.checkDate(), st)));
	}

	@Transactional
	public Optional<CheckIn> replace(Long id, CheckInUpdateRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return checkIns.findByIdWithAssociations(id).map(ci -> {
			if (req.subTaskId() != null) {
				SubTask st = subTasks.findById(req.subTaskId()).orElseThrow(
						() -> new IllegalArgumentException("subTask not found"));
				if (!st.getChallenge().getId().equals(ci.getChallenge().getId())) {
					throw new IllegalStateException("subTask does not belong to check-in challenge");
				}
				ci.setSubTask(st);
			} else {
				ci.setSubTask(null);
			}
			ci.setCheckDate(req.checkDate());
			return checkIns.save(ci);
		});
	}

	@Transactional
	public boolean delete(Long id) {
		Assert.notNull(id, "id must not be null");
		if (!checkIns.existsById(id)) {
			return false;
		}
		checkIns.deleteById(id);
		return true;
	}
}
