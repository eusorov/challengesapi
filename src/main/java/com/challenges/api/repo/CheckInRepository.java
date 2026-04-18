package com.challenges.api.repo;

import com.challenges.api.model.CheckIn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

	List<CheckIn> findByChallenge_IdOrderByCheckDateDesc(Long challengeId);
}
