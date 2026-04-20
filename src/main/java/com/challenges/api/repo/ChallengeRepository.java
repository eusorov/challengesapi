package com.challenges.api.repo;

import com.challenges.api.model.Challenge;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

	long countByIsPrivateTrue();

	@Query("select distinct c from Challenge c join fetch c.owner left join fetch c.subtasks where c.id = :id")
	Optional<Challenge> findByIdWithSubtasksAndOwner(@Param("id") Long id);

	/**
	 * Paged ids of non-private challenges only (no joins). Intended flow: call this with {@link Pageable}, then
	 * {@link #findAllWithSubtasksAndOwnerByIdIn} with {@link Page#getContent()} so fetch-joins apply only to one page.
	 */
	@Query(
			value = "select c.id from Challenge c where c.isPrivate = false order by c.id asc",
			countQuery = "select count(c) from Challenge c where c.isPrivate = false")
	Page<Long> findNonPrivateIdsOrderByIdAsc(Pageable pageable);

	/**
	 * Loads challenges with owner and subtasks for the given ids. Ordered by {@code c.id} ascending.
	 * Avoid calling with an empty collection (portable SQL for {@code IN ()} is problematic).
	 */
	@Query(
			"select distinct c from Challenge c join fetch c.owner left join fetch c.subtasks "
					+ "where c.id in :ids order by c.id asc")
	List<Challenge> findAllWithSubtasksAndOwnerByIdIn(@Param("ids") Collection<Long> ids);

	/**
	 * Challenges that ended on or before {@code maxEndDate} and have not completed check-in rollup.
	 * Used with {@code maxEndDate = today - retentionDays} so grace period after {@code end_date} is respected.
	 */
	@Query(
			value = """
					SELECT c.id FROM challenges c
					WHERE c.end_date IS NOT NULL
					AND c.end_date <= :maxEndDate
					AND NOT EXISTS (
						SELECT 1 FROM check_in_rollup_runs r
						WHERE r.challenge_id = c.id AND r.status = 'COMPLETE'
					)
					ORDER BY c.id ASC
					LIMIT :limit
					""",
			nativeQuery = true)
	List<Long> findIdsEligibleForCheckInRollup(@Param("maxEndDate") LocalDate maxEndDate, @Param("limit") int limit);
}
