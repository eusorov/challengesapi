package com.challenges.api.web.dto;

import com.challenges.api.model.Challenge;
import java.time.Instant;
import java.time.LocalDate;

public record ChallengeResponse(
		Long id,
		Long ownerUserId,
		String title,
		String description,
		LocalDate startDate,
		LocalDate endDate,
		Instant createdAt) {

	public static ChallengeResponse from(Challenge c) {
		return new ChallengeResponse(
				c.getId(),
				c.getOwner().getId(),
				c.getTitle(),
				c.getDescription(),
				c.getStartDate(),
				c.getEndDate(),
				c.getCreatedAt());
	}
}
