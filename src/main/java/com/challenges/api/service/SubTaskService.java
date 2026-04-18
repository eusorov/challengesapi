package com.challenges.api.service;

import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.web.dto.SubTaskRequest;
import com.challenges.api.web.dto.SubTaskUpdateRequest;
import java.util.List;
import java.util.Optional;
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
	public List<SubTask> listForChallenge(Long challengeId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		return subTasks.findByChallenge_IdOrderBySortIndexAsc(challengeId);
	}

	@Transactional(readOnly = true)
	public Optional<SubTask> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return subTasks.findById(id);
	}

	@Transactional
	public Optional<SubTask> create(SubTaskRequest req) {
		Assert.notNull(req, "request must not be null");
		return challenges.findById(req.challengeId()).map(ch -> subTasks.save(new SubTask(ch, req.title(), req.sortIndex())));
	}

	@Transactional
	public Optional<SubTask> replace(Long id, SubTaskUpdateRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return subTasks.findById(id).map(st -> {
			st.setTitle(req.title());
			st.setSortIndex(req.sortIndex());
			return subTasks.save(st);
		});
	}

	@Transactional
	public boolean delete(Long id) {
		Assert.notNull(id, "id must not be null");
		if (!subTasks.existsById(id)) {
			return false;
		}
		subTasks.deleteById(id);
		return true;
	}
}
