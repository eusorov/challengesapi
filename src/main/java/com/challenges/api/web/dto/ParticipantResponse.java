package com.challenges.api.web.dto;

import com.challenges.api.model.Participant;
import java.time.Instant;

public record ParticipantResponse(
		Long id, Long userId, Long challengeId, Long subTaskId, Instant joinedAt) {

	public static ParticipantResponse from(Participant p) {
		Long stId = p.getSubTask() != null ? p.getSubTask().getId() : null;
		return new ParticipantResponse(
				p.getId(), p.getUser().getId(), p.getChallenge().getId(), stId, p.getJoinedAt());
	}
}
