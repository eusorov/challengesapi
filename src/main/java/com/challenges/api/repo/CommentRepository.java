package com.challenges.api.repo;

import com.challenges.api.model.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	/**
	 * Fetches author, challenge, and subtask in one query to avoid N+1 when mapping to DTOs.
	 */
	@Query(
			"""
			select distinct c from Comment c
			join fetch c.author
			join fetch c.challenge
			left join fetch c.subTask
			where c.challenge.id = :challengeId
			order by c.createdAt desc
			""")
	List<Comment> findByChallengeIdWithAssociations(@Param("challengeId") Long challengeId);

	@Query(
			"""
			select distinct c from Comment c
			join fetch c.author
			join fetch c.challenge
			join fetch c.subTask
			where c.challenge.id = :challengeId and c.subTask.id = :subTaskId
			order by c.createdAt desc
			""")
	List<Comment> findByChallengeIdAndSubTaskIdWithAssociations(
			@Param("challengeId") Long challengeId, @Param("subTaskId") Long subTaskId);

	@Query(
			"""
			select distinct c from Comment c
			join fetch c.author
			join fetch c.challenge
			left join fetch c.subTask
			where c.id = :id
			""")
	Optional<Comment> findByIdWithAssociations(@Param("id") Long id);
}
