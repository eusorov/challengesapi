package com.challenges.api.repo;

import com.challenges.api.model.Participant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	@Query(
			value = """
					select p.id from Participant p
					where p.challenge.id = :challengeId
					order by p.id asc
					""",
			countQuery = "select count(p) from Participant p where p.challenge.id = :challengeId")
	Page<Long> findIdsForChallengeOrderByIdAsc(@Param("challengeId") Long challengeId, Pageable pageable);

	@Query(
			"""
			select distinct p from Participant p
			join fetch p.user
			join fetch p.challenge
			left join fetch p.subTask
			where p.id in :ids
			order by p.id asc
			""")
	List<Participant> findByIdInWithAssociations(@Param("ids") Collection<Long> ids);

	@Query(
			"""
			select distinct p from Participant p
			join fetch p.user
			join fetch p.challenge
			left join fetch p.subTask
			where p.user.id = :userId and p.challenge.id = :challengeId and p.subTask is null
			""")
	Optional<Participant> findChallengeWideWithAssociations(
			@Param("userId") Long userId, @Param("challengeId") Long challengeId);

	@Query(
			"""
			select distinct p from Participant p
			join fetch p.user
			join fetch p.challenge
			join fetch p.subTask
			where p.user.id = :userId and p.challenge.id = :challengeId and p.subTask.id = :subTaskId
			""")
	Optional<Participant> findSubTaskScopedWithAssociations(
			@Param("userId") Long userId,
			@Param("challengeId") Long challengeId,
			@Param("subTaskId") Long subTaskId);

	List<Participant> findByChallenge_IdAndSubTaskIsNull(Long challengeId);

	List<Participant> findBySubTask_Id(Long subTaskId);

	boolean existsByUser_IdAndChallenge_IdAndSubTaskIsNull(Long userId, Long challengeId);

	boolean existsByUser_IdAndChallenge_IdAndSubTask_Id(Long userId, Long challengeId, Long subTaskId);

	/** Any participant row for this user and challenge (challenge-wide or subtask-scoped). */
	boolean existsByUser_IdAndChallenge_Id(Long userId, Long challengeId);
}
