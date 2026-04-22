package com.challenges.api.service;

import com.challenges.api.config.CheckInRetentionProperties;
import com.challenges.api.model.CheckInRollupRun;
import com.challenges.api.model.RollupStatus;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.CheckInRollupRunRepository;
import com.challenges.api.repo.CheckInSummaryRepository;
import java.time.LocalDate;
import java.util.List;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInRollupService {

	private static final Logger log = LoggerFactory.getLogger(CheckInRollupService.class);

	private final ChallengeRepository challenges;
	private final CheckInRepository checkIns;
	private final CheckInSummaryRepository summaries;
	private final CheckInRollupRunRepository rollupRuns;
	private final CheckInRetentionProperties retentionProps;

	public CheckInRollupService(
			ChallengeRepository challenges,
			CheckInRepository checkIns,
			CheckInSummaryRepository summaries,
			CheckInRollupRunRepository rollupRuns,
			CheckInRetentionProperties retentionProps) {
		this.challenges = challenges;
		this.checkIns = checkIns;
		this.summaries = summaries;
		this.rollupRuns = rollupRuns;
		this.retentionProps = retentionProps;
	}

	/**
	 * Processes up to {@link CheckInRetentionProperties#rollupBatchSize()} challenges that are past the
	 * retention grace period.
	 */
	public void runBatch() {
		LocalDate maxEndDate = LocalDate.now(ZoneId.systemDefault()).minusDays(retentionProps.retentionDaysAfterChallengeEnd());
		List<Long> ids =
				challenges.findIdsEligibleForCheckInRollup(maxEndDate, retentionProps.rollupBatchSize());
		for (Long challengeId : ids) {
			try {
				rollupChallenge(challengeId);
			} catch (RuntimeException e) {
				log.error("Check-in rollup failed for challenge {}", challengeId, e);
			}
		}
	}

	/**
	 * Summarizes and deletes raw {@code check_ins} for one challenge. Idempotent if already {@link RollupStatus#COMPLETE}.
	 */
	@Transactional
	public void rollupChallenge(long challengeId) {
		if (rollupRuns.existsByChallengeIdAndStatus(challengeId, RollupStatus.COMPLETE)) {
			return;
		}

		CheckInRollupRun run = rollupRuns
				.findById(challengeId)
				.orElseGet(() -> new CheckInRollupRun(challengeId, RollupStatus.PENDING));
		run.setStatus(RollupStatus.PENDING);
		run.setErrorMessage(null);
		rollupRuns.save(run);

		try {
			summaries.deleteByChallenge_Id(challengeId);
			checkIns.insertSummariesFromCheckIns(challengeId);
			checkIns.deleteByChallenge_Id(challengeId);
			run.setStatus(RollupStatus.COMPLETE);
			run.setErrorMessage(null);
			rollupRuns.save(run);
			log.debug("Check-in rollup complete for challenge {}", challengeId);
		} catch (RuntimeException e) {
			run.setStatus(RollupStatus.FAILED);
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
			if (msg.length() > 2000) {
				msg = msg.substring(0, 2000);
			}
			run.setErrorMessage(msg);
			rollupRuns.save(run);
			log.error("Check-in rollup failed for challenge {}", challengeId, e);
		}
	}

	@Transactional(readOnly = true)
	public boolean isRolledUp(long challengeId) {
		return rollupRuns.existsByChallengeIdAndStatus(challengeId, RollupStatus.COMPLETE);
	}
}
