package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CheckInRequest(
		@NotNull Long userId,
		@NotNull Long challengeId,
		@NotNull LocalDate checkDate,
		Long subTaskId) {}
