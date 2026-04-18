package com.challenges.api.web.dto;

import com.challenges.api.model.Comment;
import java.time.Instant;

public record CommentResponse(
		Long id,
		Long userId,
		Long challengeId,
		Long subTaskId,
		String body,
		Instant createdAt) {

	public static CommentResponse from(Comment c) {
		Long stId = c.getSubTask() != null ? c.getSubTask().getId() : null;
		return new CommentResponse(
				c.getId(),
				c.getAuthor().getId(),
				c.getChallenge().getId(),
				stId,
				c.getBody(),
				c.getCreatedAt());
	}
}
