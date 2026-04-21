package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.support.ChallengeConstraints;
import com.challenges.api.web.dto.SubTaskRequest;
import com.challenges.api.web.dto.SubTaskUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubTaskService {

	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public SubTaskService(ChallengeRepository challenges, SubTaskRepository subTasks) {
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@Transactional(readOnly = true)
	public @NonNull List<SubTask> listForChallenge(@NonNull Long challengeId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		return subTasks.findByChallenge_IdOrderBySortIndexAsc(challengeId);
	}

	@Transactional(readOnly = true)
	public Optional<SubTask> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return subTasks.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<SubTask> create(@NonNull SubTaskRequest req, @NonNull Long actorUserId) {
		Assert.notNull(req, "request must not be null");
		Assert.notNull(actorUserId, "actorUserId must not be null");
		return challenges.findByIdWithSubtasksAndOwner(req.challengeId()).map(ch -> {
			assertActorOwnsChallenge(actorUserId, ch);
			if (subTasks.countByChallenge_Id(ch.getId()) >= ChallengeConstraints.MAX_SUBTASKS_PER_CHALLENGE) {
				throw new IllegalArgumentException(
						"A challenge cannot have more than "
								+ ChallengeConstraints.MAX_SUBTASKS_PER_CHALLENGE
								+ " subtasks");
			}
			return subTasks.save(new SubTask(ch, req.title(), req.sortIndex()));
		});
	}

	@Transactional
	public Optional<SubTask> replace(@NonNull Long id, @NonNull SubTaskUpdateRequest req, @NonNull Long actorUserId) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		Assert.notNull(actorUserId, "actorUserId must not be null");
		return subTasks.findByIdWithAssociations(id).map(st -> {
			assertActorOwnsChallenge(actorUserId, st.getChallenge());
			st.setTitle(req.title());
			st.setSortIndex(req.sortIndex());
			return subTasks.save(st);
		});
	}

	@Transactional
	public boolean delete(@NonNull Long id, @NonNull Long actorUserId) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(actorUserId, "actorUserId must not be null");
		Optional<SubTask> loaded = subTasks.findByIdWithAssociations(id);
		if (loaded.isEmpty()) {
			return false;
		}
		assertActorOwnsChallenge(actorUserId, loaded.get().getChallenge());
		subTasks.deleteById(id);
		return true;
	}

	private void assertActorOwnsChallenge(@NonNull Long actorUserId, @NonNull Challenge challenge) {
		if (!challenge.getOwner().getId().equals(actorUserId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
	}
}
