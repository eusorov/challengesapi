package com.challenges.api.web.dto;

import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import java.time.Instant;

public record InviteResponse(
		Long id,
		Long inviterUserId,
		Long inviteeUserId,
		Long challengeId,
		Long subTaskId,
		InviteStatus status,
		Instant createdAt,
		Instant expiresAt) {

	public static InviteResponse from(Invite inv) {
		Long stId = inv.getSubTask() != null ? inv.getSubTask().getId() : null;
		return new InviteResponse(
				inv.getId(),
				inv.getInviter().getId(),
				inv.getInvitee().getId(),
				inv.getChallenge().getId(),
				stId,
				inv.getStatus(),
				inv.getCreatedAt(),
				inv.getExpiresAt());
	}
}
