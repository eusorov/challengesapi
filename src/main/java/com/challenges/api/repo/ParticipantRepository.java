package com.challenges.api.repo;

import com.challenges.api.model.Participant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	List<Participant> findByChallenge_Id(Long challengeId);

	List<Participant> findByChallenge_IdAndSubTaskIsNull(Long challengeId);

	List<Participant> findBySubTask_Id(Long subTaskId);
}
