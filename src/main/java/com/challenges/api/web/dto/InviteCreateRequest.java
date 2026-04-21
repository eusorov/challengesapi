package com.challenges.api.web.dto;

import com.challenges.api.model.InviteStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Body for {@code POST /api/invites}: inviter is the JWT subject; invitee is resolved by email. */
public record InviteCreateRequest(
		@NotBlank @Email String inviteeEmail,
		@NotNull Long challengeId,
		@Nullable Long subTaskId,
		@Nullable InviteStatus status,
		@Nullable Instant expiresAt) {}
