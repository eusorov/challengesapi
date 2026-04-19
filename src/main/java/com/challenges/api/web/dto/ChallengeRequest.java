package com.challenges.api.web.dto;

import com.challenges.api.model.ChallengeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record ChallengeRequest(
		@NotNull Long ownerUserId,
		@NotBlank String title,
		@Nullable String description,
		@NotNull LocalDate startDate,
		@Nullable LocalDate endDate,
		@NotNull ChallengeCategory category) {}
