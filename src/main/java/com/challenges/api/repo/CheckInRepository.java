package com.challenges.api.repo;

import com.challenges.api.model.CheckIn;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

	long countByChallenge_Id(Long challengeId);

	@Query(
			value = """
					select ci.id from CheckIn ci
					where ci.challenge.id = :challengeId
					order by ci.checkDate desc, ci.id desc
					""",
			countQuery = "select count(ci) from CheckIn ci where ci.challenge.id = :challengeId")
	Page<Long> findIdsForChallengeOrderByCheckDateDesc(
			@Param("challengeId") Long challengeId, Pageable pageable);

	@Query(
			"""
			select distinct ci from CheckIn ci
			join fetch ci.user
			join fetch ci.challenge
			left join fetch ci.subTask
			where ci.id in :ids
			order by ci.checkDate desc, ci.id desc
			""")
	List<CheckIn> findByIdInWithAssociations(@Param("ids") Collection<Long> ids);

	@Query(
			"""
			select distinct ci from CheckIn ci
			join fetch ci.user
			join fetch ci.challenge
			left join fetch ci.subTask
			where ci.id = :id
			""")
	Optional<CheckIn> findByIdWithAssociations(@Param("id") Long id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from CheckIn ci where ci.challenge.id = :challengeId")
	int deleteByChallenge_Id(@Param("challengeId") Long challengeId);

	@Query(
			value = """
					INSERT INTO check_in_summaries (user_id, challenge_id, subtask_id, total_check_ins, first_check_in_date, last_check_in_date, rolled_up_at)
					SELECT user_id, challenge_id, subtask_id, COUNT(*)::bigint, MIN(check_date), MAX(check_date), NOW()
					FROM check_ins
					WHERE challenge_id = :challengeId
					GROUP BY user_id, challenge_id, subtask_id
					""",
			nativeQuery = true)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	int insertSummariesFromCheckIns(@Param("challengeId") Long challengeId);
}
