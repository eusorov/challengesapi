package com.challenges.api.service;

import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.support.ChallengeConstraints;
import com.challenges.api.web.dto.SubTaskRequest;
import com.challenges.api.web.dto.SubTaskUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

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
	public Optional<SubTask> create(@NonNull SubTaskRequest req) {
		Assert.notNull(req, "request must not be null");
		return challenges.findById(req.challengeId()).map(ch -> {
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
	public Optional<SubTask> replace(@NonNull Long id, @NonNull SubTaskUpdateRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return subTasks.findByIdWithAssociations(id).map(st -> {
			st.setTitle(req.title());
			st.setSortIndex(req.sortIndex());
			return subTasks.save(st);
		});
	}

	@Transactional
	public boolean delete(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		if (!subTasks.existsById(id)) {
			return false;
		}
		subTasks.deleteById(id);
		return true;
	}
}
