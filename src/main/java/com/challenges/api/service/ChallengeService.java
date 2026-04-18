package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.ChallengeRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class ChallengeService {

	private final UserRepository users;
	private final ChallengeRepository challenges;

	public ChallengeService(UserRepository users, ChallengeRepository challenges) {
		this.users = users;
		this.challenges = challenges;
	}

	@Transactional(readOnly = true)
	public List<Challenge> listChallenges() {
		return challenges.findAll();
	}

	@Transactional(readOnly = true)
	public Optional<Challenge> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return challenges.findById(id);
	}

	@Transactional
	public Optional<Challenge> create(ChallengeRequest req) {
		Assert.notNull(req, "request must not be null");
		return users.findById(req.ownerUserId()).map(owner -> challenges.save(new Challenge(owner,
						req.title(),
						req.description(),
						req.startDate(),
						req.endDate())));
	}

	@Transactional
	public Optional<Challenge> replace(Long id, ChallengeRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return users.findById(req.ownerUserId()).flatMap(owner -> challenges.findById(id).map(ch -> {
			ch.setOwner(owner);
			ch.setTitle(req.title());
			ch.setDescription(req.description());
			ch.setStartDate(req.startDate());
			ch.setEndDate(req.endDate());
			return challenges.save(ch);
		}));
	}

	@Transactional
	public boolean delete(Long id) {
		Assert.notNull(id, "id must not be null");
		if (!challenges.existsById(id)) {
			return false;
		}
		challenges.deleteById(id);
		return true;
	}
}
