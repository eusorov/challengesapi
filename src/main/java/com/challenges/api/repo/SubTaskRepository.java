package com.challenges.api.repo;

import com.challenges.api.model.SubTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {

	@Query(
			"""
			select distinct st from SubTask st
			join fetch st.challenge
			where st.challenge.id = :challengeId
			order by st.sortIndex asc
			""")
	List<SubTask> findByChallenge_IdOrderBySortIndexAsc(@Param("challengeId") Long challengeId);

	@Query(
			"""
			select distinct st from SubTask st
			join fetch st.challenge
			where st.id = :id
			""")
	Optional<SubTask> findByIdWithAssociations(@Param("id") Long id);
}
