package com.challenges.api.service;

import com.challenges.api.model.CheckIn;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.CheckInSummaryRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.CheckInRequest;
import com.challenges.api.web.dto.CheckInSummaryResponse;
import com.challenges.api.web.dto.CheckInUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class CheckInService {

	private final CheckInRepository checkIns;
	private final CheckInSummaryRepository checkInSummaries;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final CheckInRollupService checkInRollupService;

	public CheckInService(
			CheckInRepository checkIns,
			CheckInSummaryRepository checkInSummaries,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			CheckInRollupService checkInRollupService) {
		this.checkIns = checkIns;
		this.checkInSummaries = checkInSummaries;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.checkInRollupService = checkInRollupService;
	}

	/**
	 * Per-day rows from {@code check_ins}. Empty once this challenge has completed check-in rollup; use
	 * {@link #listSummariesForRolledUpChallenge(Long)} for aggregated data after rollup.
	 */
	@Transactional(readOnly = true)
	public @NonNull List<CheckInSummaryResponse> listSummariesForRolledUpChallenge(@NonNull Long challengeId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		if (!challenges.existsById(challengeId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		if (!checkInRollupService.isRolledUp(challengeId)) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND, "Summaries exist only after check-in rollup for eligible ended challenges.");
		}
		return checkInSummaries.findByChallenge_IdWithAssociations(challengeId).stream()
				.map(CheckInSummaryResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public @NonNull Page<CheckIn> listForChallenge(@NonNull Long challengeId, @NonNull Pageable pageable) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(pageable, "pageable must not be null");
		Page<Long> idPage = checkIns.findIdsForChallengeOrderByCheckDateDesc(challengeId, pageable);
		if (idPage.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
		}
		return new PageImpl<>(
				checkIns.findByIdInWithAssociations(idPage.getContent()), pageable, idPage.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Optional<CheckIn> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return checkIns.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<CheckIn> create(@NonNull CheckInRequest req) {
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
	public Optional<CheckIn> replace(@NonNull Long id, @NonNull CheckInUpdateRequest req) {
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
	public boolean delete(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		if (!checkIns.existsById(id)) {
			return false;
		}
		checkIns.deleteById(id);
		return true;
	}
}
