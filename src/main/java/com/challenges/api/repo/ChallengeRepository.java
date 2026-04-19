package com.challenges.api.repo;

import com.challenges.api.model.Challenge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

	/**
	 * Join-fetch owner and subtasks so listing challenges does not N+1 when mapping or traversing
	 * {@link com.challenges.api.model.Challenge#getSubtasks()} / {@link com.challenges.api.web.dto.ChallengeResponse}.
	 */
	@Query(
			"select distinct c from Challenge c join fetch c.owner left join fetch c.subtasks order by c.id asc")
	List<Challenge> findAllWithSubtasksAndOwner();

	@Query("select distinct c from Challenge c join fetch c.owner left join fetch c.subtasks where c.id = :id")
	Optional<Challenge> findByIdWithSubtasksAndOwner(@Param("id") Long id);
}
