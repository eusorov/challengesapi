package com.challenges.api.repo;

import com.challenges.api.model.CheckInSummary;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInSummaryRepository extends JpaRepository<CheckInSummary, Long> {

	long countByChallenge_Id(Long challengeId);

	@Query(
			"""
			select s from CheckInSummary s
			join fetch s.user
			join fetch s.challenge
			left join fetch s.subTask
			where s.challenge.id = :challengeId
			order by s.user.id asc, s.id asc
			""")
	List<CheckInSummary> findByChallenge_IdWithAssociations(@Param("challengeId") Long challengeId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from CheckInSummary s where s.challenge.id = :challengeId")
	int deleteByChallenge_Id(@Param("challengeId") Long challengeId);
}
