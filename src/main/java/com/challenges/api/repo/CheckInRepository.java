package com.challenges.api.repo;

import com.challenges.api.model.CheckIn;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

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
}
