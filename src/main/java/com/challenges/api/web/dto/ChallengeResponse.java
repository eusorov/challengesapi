package com.challenges.api.web.dto;

import com.challenges.api.model.Challenge;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ChallengeResponse(
		Long id,
		Long ownerUserId,
		String title,
		String description,
		LocalDate startDate,
		LocalDate endDate,
		Instant createdAt,
		String imageObjectKey,
		String imageUrl,
		/** Subtasks for this challenge (empty when none). Loaded with list/get via repository fetch-join. */
		List<SubTaskResponse> subtasks) {

	public static ChallengeResponse from(Challenge c) {
		return from(c, null);
	}

	public static ChallengeResponse from(Challenge c, String imagePublicBaseUrl) {
		String key = c.getImageObjectKey();
		String url = null;
		if (key != null && imagePublicBaseUrl != null && !imagePublicBaseUrl.isBlank()) {
			url = imagePublicBaseUrl.replaceAll("/$", "") + "/" + key;
		}
		List<SubTaskResponse> subtasks =
				c.getSubtasks().stream().map(SubTaskResponse::from).toList();
		return new ChallengeResponse(
				c.getId(),
				c.getOwner().getId(),
				c.getTitle(),
				c.getDescription(),
				c.getStartDate(),
				c.getEndDate(),
				c.getCreatedAt(),
				key,
				url,
				subtasks);
	}
}
