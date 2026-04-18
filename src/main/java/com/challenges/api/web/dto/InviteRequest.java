package com.challenges.api.web.dto;

import com.challenges.api.model.InviteStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record InviteRequest(
		@NotNull Long inviterUserId,
		@NotNull Long inviteeUserId,
		@NotNull Long challengeId,
		@Nullable Long subTaskId,
		@Nullable InviteStatus status,
		@Nullable Instant expiresAt) {}
