package com.challenges.api.web.dto;

import com.challenges.api.model.InviteStatus;
import java.time.Instant;

public record InviteUpdateRequest(InviteStatus status, Instant expiresAt) {}
