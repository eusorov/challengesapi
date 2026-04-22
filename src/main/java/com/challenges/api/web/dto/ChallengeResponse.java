package com.challenges.api.web.dto;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.support.ChallengeLocationMapping;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Response for {@code GET /api/challenges/{id}}: viewer is the JWT subject.
 * @param id the ID of the challenge
 * @param ownerUserId the ID of the user who is the owner of the challenge
 * @param title the title of the challenge
 * @param description the description of the challenge
 * @param category the category of the challenge
 * @param isPrivate whether the challenge is private
 * @param city the city of the challenge
 * @param location the location of the challenge
 * @param startDate the start date of the challenge
 * @param endDate the end date of the challenge
 * @param createdAt the creation date of the challenge
 * @param imageObjectKey the key of the image object in the S3 bucket
 * @param imageUrl the URL of the image
 * @param subtasks the subtasks of the challenge, empty when none. Loaded with list/get via repository fetch-join.
 */
public record ChallengeResponse(
		Long id,
		Long ownerUserId,
		String title,
		@Nullable String description,
		ChallengeCategory category,
		@JsonProperty("private") boolean isPrivate,
		@Nullable String city,
		@Nullable ChallengeLocationDto location,
		LocalDate startDate,
		@Nullable LocalDate endDate,
		Instant createdAt,
		@Nullable String imageObjectKey,
		@Nullable String imageUrl,
		List<SubTaskResponse> subtasks) {

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
				c.getCategory(),
				c.isPrivate(),
				c.getCity(),
				ChallengeLocationMapping.fromPoint(c.getLocation()),
				c.getStartDate(),
				c.getEndDate(),
				c.getCreatedAt(),
				key,
				url,
				subtasks);
	}
}
