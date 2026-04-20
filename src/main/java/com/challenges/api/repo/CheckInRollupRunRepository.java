package com.challenges.api.repo;

import com.challenges.api.model.CheckInRollupRun;
import com.challenges.api.model.RollupStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRollupRunRepository extends JpaRepository<CheckInRollupRun, Long> {

	Optional<CheckInRollupRun> findByChallengeIdAndStatus(Long challengeId, RollupStatus status);

	boolean existsByChallengeIdAndStatus(Long challengeId, RollupStatus status);
}
