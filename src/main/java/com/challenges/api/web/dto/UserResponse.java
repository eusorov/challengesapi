package com.challenges.api.web.dto;

import com.challenges.api.model.User;
import java.time.Instant;

public record UserResponse(Long id, String email, Instant createdAt) {

	public static UserResponse from(User u) {
		return new UserResponse(u.getId(), u.getEmail(), u.getCreatedAt());
	}
}
