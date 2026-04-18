package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Comment;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CommentRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.CommentRequest;
import com.challenges.api.web.dto.CommentUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class CommentService {

	private final CommentRepository comments;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public CommentService(
			CommentRepository comments,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks) {
		this.comments = comments;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@Transactional(readOnly = true)
	public @NonNull List<Comment> listForChallenge(Long challengeId, @Nullable Long subTaskIdFilter) {
		Assert.notNull(challengeId, "challengeId must not be null");
		if (subTaskIdFilter == null) {
			return comments.findByChallengeIdWithAssociations(challengeId);
		}
		Optional<SubTask> st = subTasks.findById(subTaskIdFilter);
		if (st.isEmpty() || !st.get().getChallenge().getId().equals(challengeId)) {
			return List.of();
		}
		return comments.findByChallengeIdAndSubTaskIdWithAssociations(challengeId, subTaskIdFilter);
	}

	@Transactional
	public Optional<Comment> create(Long challengeId, CommentRequest req) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(req, "req must not be null");
		Optional<User> author = users.findById(req.userId());
		Optional<Challenge> challenge = challenges.findById(challengeId);
		if (author.isEmpty() || challenge.isEmpty()) {
			return Optional.empty();
		}
		if (req.subTaskId() == null) {
			return Optional.of(comments.save(new Comment(author.get(), challenge.get(), req.body())));
		}
		Optional<SubTask> sub = subTasks.findById(req.subTaskId());
		if (sub.isEmpty() || !sub.get().getChallenge().getId().equals(challengeId)) {
			return Optional.empty();
		}
		return Optional.of(
				comments.save(new Comment(author.get(), challenge.get(), sub.get(), req.body())));
	}

	@Transactional(readOnly = true)
	public Optional<Comment> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return comments.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<Comment> update(Long id, CommentUpdateRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "req must not be null");
		return comments.findByIdWithAssociations(id).map(c -> {
			c.setBody(req.body());
			return comments.save(c);
		});
	}

	@Transactional
	public boolean delete(Long id) {
		Assert.notNull(id, "id must not be null");
		if (!comments.existsById(id)) {
			return false;
		}
		comments.deleteById(id);
		return true;
	}
}
