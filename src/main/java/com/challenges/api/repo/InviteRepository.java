package com.challenges.api.repo;

import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteRepository extends JpaRepository<Invite, Long> {

	List<Invite> findByInvitee_IdAndStatus(Long inviteeUserId, InviteStatus status);

	List<Invite> findByInvitee_IdAndChallenge_IdAndStatusOrderByIdAsc(
			Long inviteeId, Long challengeId, InviteStatus status);

	@Query(value = "select i.id from Invite i order by i.id asc", countQuery = "select count(i) from Invite i")
	Page<Long> findIdsOrderByIdAsc(Pageable pageable);

	@Query(
			value = """
					select i.id from Invite i
					where i.challenge.id = :challengeId
					order by i.id asc
					""",
			countQuery = "select count(i) from Invite i where i.challenge.id = :challengeId")
	Page<Long> findIdsForChallengeOrderByIdAsc(@Param("challengeId") Long challengeId, Pageable pageable);

	@Query(
			"""
			select distinct i from Invite i
			join fetch i.inviter
			join fetch i.invitee
			join fetch i.challenge
			left join fetch i.subTask
			where i.id in :ids
			order by i.id asc
			""")
	List<Invite> findByIdInWithAssociations(@Param("ids") Collection<Long> ids);

	@Query(
			"""
			select distinct i from Invite i
			join fetch i.inviter
			join fetch i.invitee
			join fetch i.challenge
			left join fetch i.subTask
			where i.id = :id
			""")
	Optional<Invite> findByIdWithAssociations(@Param("id") Long id);
}
