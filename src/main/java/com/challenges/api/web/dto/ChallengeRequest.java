package com.challenges.api.web.dto;

import com.challenges.api.model.ChallengeCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Body for {@code POST /api/challenges}: owner is the JWT subject.
 * @param ownerUserId the ID of the user who is the owner of the challenge
 * @param title the title of the challenge
 * @param description the description of the challenge
 * @param startDate the start date of the challenge
 * @param endDate the end date of the challenge
 * @param category the category of the challenge
 * @param city the city of the challenge
 * @param location the location of the challenge
 * @param isPrivate whether the challenge is private, when omitted or null, treated as not private (false).
 */
public record ChallengeRequest(
		@NotNull Long ownerUserId,
		@NotBlank String title,
		@Nullable String description,
		@NotNull LocalDate startDate,
		@Nullable LocalDate endDate,
		@NotNull ChallengeCategory category,
		@Nullable String city, 
		@Valid @Nullable ChallengeLocationDto location,
		@JsonProperty("private") @Nullable Boolean isPrivate) {}
