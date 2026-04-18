package com.challenges.api.web.dto;

import com.challenges.api.model.InviteStatus;
import java.time.Instant;
import org.springframework.lang.Nullable;

public record InviteUpdateRequest(@Nullable InviteStatus status, @Nullable Instant expiresAt) {}
