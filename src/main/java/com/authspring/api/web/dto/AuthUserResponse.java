package com.authspring.api.web.dto;

import com.challenges.api.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthUserResponse(
		Long id,
		String name,
		String email,
		String role,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("updated_at") Instant updatedAt) {

	public static AuthUserResponse fromEntity(User user) {
		return new AuthUserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
