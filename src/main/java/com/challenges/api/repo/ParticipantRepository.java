package com.challenges.api.repo;

import com.challenges.api.model.Participant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	@Query(
			"""
			select distinct p from Participant p
			join fetch p.user
			join fetch p.challenge
			left join fetch p.subTask
			where p.challenge.id = :challengeId
			""")
	List<Participant> findByChallenge_Id(@Param("challengeId") Long challengeId);

	List<Participant> findByChallenge_IdAndSubTaskIsNull(Long challengeId);

	List<Participant> findBySubTask_Id(Long subTaskId);

	boolean existsByUser_IdAndChallenge_IdAndSubTaskIsNull(Long userId, Long challengeId);

	boolean existsByUser_IdAndChallenge_IdAndSubTask_Id(Long userId, Long challengeId, Long subTaskId);
}
