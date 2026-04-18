package com.challenges.api.repo;

import com.challenges.api.model.Schedule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

	/**
	 * Loads challenge/subtask owners and weekday rows in one go for API mapping (avoids N+1 on
	 * {@link com.challenges.api.web.dto.ScheduleResponse}).
	 */
	@Query(
			"""
			select distinct s from Schedule s
			left join fetch s.challenge
			left join fetch s.subTask
			left join fetch s.weekDays
			where s.id = :id
			""")
	Optional<Schedule> findByIdWithAssociations(@Param("id") Long id);
}
