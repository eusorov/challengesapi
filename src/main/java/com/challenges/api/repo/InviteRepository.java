package com.challenges.api.repo;

import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteRepository extends JpaRepository<Invite, Long> {

	List<Invite> findByInvitee_IdAndStatus(Long inviteeUserId, InviteStatus status);

	@Query(
			"""
			select distinct i from Invite i
			join fetch i.inviter
			join fetch i.invitee
			join fetch i.challenge
			left join fetch i.subTask
			where i.challenge.id = :challengeId
			""")
	List<Invite> findByChallenge_Id(@Param("challengeId") Long challengeId);

	@Query(
			"""
			select distinct i from Invite i
			join fetch i.inviter
			join fetch i.invitee
			join fetch i.challenge
			left join fetch i.subTask
			""")
	List<Invite> findAllWithAssociations();

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
