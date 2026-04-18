package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record CheckInRequest(
		@NotNull Long userId,
		@NotNull Long challengeId,
		@NotNull LocalDate checkDate,
		@Nullable Long subTaskId) {}
