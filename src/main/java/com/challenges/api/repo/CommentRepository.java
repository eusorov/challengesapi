package com.challenges.api.repo;

import com.challenges.api.model.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Query(
			value = """
					select c.id from Comment c
					where c.challenge.id = :challengeId
					order by c.createdAt desc, c.id desc
					""",
			countQuery = "select count(c) from Comment c where c.challenge.id = :challengeId")
	Page<Long> findIdsForChallengeOrderByCreatedAtDesc(
			@Param("challengeId") Long challengeId, Pageable pageable);

	@Query(
			value = """
					select c.id from Comment c
					where c.challenge.id = :challengeId and c.subTask.id = :subTaskId
					order by c.createdAt desc, c.id desc
					""",
			countQuery = """
					select count(c) from Comment c
					where c.challenge.id = :challengeId and c.subTask.id = :subTaskId
					""")
	Page<Long> findIdsForChallengeAndSubTaskOrderByCreatedAtDesc(
			@Param("challengeId") Long challengeId, @Param("subTaskId") Long subTaskId, Pageable pageable);

	@Query(
			"""
			select distinct c from Comment c
			join fetch c.author
			join fetch c.challenge
			left join fetch c.subTask
			where c.id in :ids
			order by c.createdAt desc, c.id desc
			""")
	List<Comment> findByIdInWithAssociations(@Param("ids") Collection<Long> ids);

	@Query(
			"""
			select distinct c from Comment c
			join fetch c.author
			join fetch c.challenge
			join fetch c.subTask
			where c.id in :ids
			order by c.createdAt desc, c.id desc
			""")
	List<Comment> findByIdInWithAssociationsSubTaskRequired(@Param("ids") Collection<Long> ids);

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
