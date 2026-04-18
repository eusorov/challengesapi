package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ChallengeRequest(
		@NotNull Long ownerUserId,
		@NotBlank String title,
		String description,
		@NotNull LocalDate startDate,
		LocalDate endDate) {}
