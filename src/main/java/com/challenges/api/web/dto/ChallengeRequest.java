package com.challenges.api.web.dto;

import com.challenges.api.model.ChallengeCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
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
		@NotNull ChallengeCategory category,
		/** When omitted or null, treated as not private (false). */
		@JsonProperty("private") @Nullable Boolean isPrivate) {}
