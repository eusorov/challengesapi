package com.challenges.api.web.dto;

import com.challenges.api.model.InviteStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record InviteRequest(
		@NotNull Long inviterUserId,
		@NotNull Long inviteeUserId,
		@NotNull Long challengeId,
		Long subTaskId,
		InviteStatus status,
		Instant expiresAt) {}
