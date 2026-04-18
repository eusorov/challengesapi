package com.challenges.api.repo;

import com.challenges.api.model.SubTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {

	List<SubTask> findByChallenge_IdOrderBySortIndexAsc(Long challengeId);
}
