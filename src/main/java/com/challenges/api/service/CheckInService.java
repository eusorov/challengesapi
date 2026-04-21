package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.CheckIn;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.CheckInSummaryRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.CheckInRequest;
import com.challenges.api.web.dto.CheckInSummaryResponse;
import com.challenges.api.web.dto.CheckInUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
	private final ChallengeService challengeService;
	private final ParticipantRepository participants;

	public CheckInService(
			CheckInRepository checkIns,
			CheckInSummaryRepository checkInSummaries,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			CheckInRollupService checkInRollupService,
			ChallengeService challengeService,
			ParticipantRepository participants) {
		this.checkIns = checkIns;
		this.checkInSummaries = checkInSummaries;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.checkInRollupService = checkInRollupService;
		this.challengeService = challengeService;
		this.participants = participants;
	}

	/**
	 * Per-day rows from {@code check_ins}. Empty once this challenge has completed check-in rollup; use
	 * {@link #listSummariesForRolledUpChallenge(Long, Long)} for aggregated data after rollup.
	 */
	@Transactional(readOnly = true)
	public @NonNull List<CheckInSummaryResponse> listSummariesForRolledUpChallenge(
			@NonNull Long challengeId, @Nullable Long viewerUserId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		assertViewerMayReadCheckInsForChallenge(challengeId, viewerUserId);
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
	public @NonNull Page<CheckIn> listForChallenge(
			@NonNull Long challengeId, @Nullable Long viewerUserId, @NonNull Pageable pageable) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(pageable, "pageable must not be null");
		assertViewerMayReadCheckInsForChallenge(challengeId, viewerUserId);
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

	@Transactional(readOnly = true)
	public Optional<CheckIn> findByIdForViewer(@NonNull Long id, @Nullable Long viewerUserId) {
		Assert.notNull(id, "id must not be null");
		Optional<CheckIn> loaded = checkIns.findByIdWithAssociations(id);
		if (loaded.isEmpty()) {
			return Optional.empty();
		}
		assertViewerMayReadCheckInsForChallenge(loaded.get().getChallenge().getId(), viewerUserId);
		return loaded;
	}

	private void assertViewerMayReadCheckInsForChallenge(@NonNull Long challengeId, @Nullable Long viewerUserId) {
		if (viewerUserId == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		Challenge challenge =
				challengeService
						.findByIdForViewer(challengeId, viewerUserId)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (challenge.getOwner().getId().equals(viewerUserId)) {
			return;
		}
		if (participants.existsByUser_IdAndChallenge_Id(viewerUserId, challengeId)) {
			return;
		}
		throw new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	@Transactional
	public Optional<CheckIn> create(@NonNull CheckInRequest req, @NonNull Long actorUserId) {
		Assert.notNull(req, "request must not be null");
		Assert.notNull(actorUserId, "actorUserId must not be null");
		if (!actorUserId.equals(req.userId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
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
		assertActorMayCreateCheckIn(actorUserId, challenge.get(), st);
		return Optional.of(checkIns.save(new CheckIn(user.get(), challenge.get(), req.checkDate(), st)));
	}

	private void assertActorMayCreateCheckIn(
			@NonNull Long userId, @NonNull Challenge challenge, @Nullable SubTask subTaskOrNull) {
		if (challenge.getOwner().getId().equals(userId)) {
			return;
		}
		if (subTaskOrNull == null) {
			if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(userId, challenge.getId())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN);
			}
			return;
		}
		boolean challengeWide =
				participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(userId, challenge.getId());
		boolean subTaskScoped =
				participants.existsByUser_IdAndChallenge_IdAndSubTask_Id(
						userId, challenge.getId(), subTaskOrNull.getId());
		if (!challengeWide && !subTaskScoped) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
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
