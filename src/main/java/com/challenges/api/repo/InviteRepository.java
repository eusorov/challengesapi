package com.challenges.api.repo;

import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteRepository extends JpaRepository<Invite, Long> {

	List<Invite> findByInvitee_IdAndStatus(Long inviteeUserId, InviteStatus status);

	List<Invite> findByChallenge_Id(Long challengeId);
}
