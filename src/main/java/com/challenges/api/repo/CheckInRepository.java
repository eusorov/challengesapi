package com.challenges.api.repo;

import com.challenges.api.model.CheckIn;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

	@Query(
			"""
			select distinct ci from CheckIn ci
			join fetch ci.user
			join fetch ci.challenge
			left join fetch ci.subTask
			where ci.challenge.id = :challengeId
			order by ci.checkDate desc
			""")
	List<CheckIn> findByChallenge_IdOrderByCheckDateDesc(@Param("challengeId") Long challengeId);

	@Query(
			"""
			select distinct ci from CheckIn ci
			join fetch ci.user
			join fetch ci.challenge
			left join fetch ci.subTask
			where ci.id = :id
			""")
	Optional<CheckIn> findByIdWithAssociations(@Param("id") Long id);
}
