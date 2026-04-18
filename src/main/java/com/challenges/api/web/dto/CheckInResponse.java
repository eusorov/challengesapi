package com.challenges.api.web.dto;

import com.challenges.api.model.CheckIn;
import java.time.LocalDate;

public record CheckInResponse(
		Long id, Long userId, Long challengeId, LocalDate checkDate, Long subTaskId) {

	public static CheckInResponse from(CheckIn c) {
		Long stId = c.getSubTask() != null ? c.getSubTask().getId() : null;
		return new CheckInResponse(
				c.getId(), c.getUser().getId(), c.getChallenge().getId(), c.getCheckDate(), stId);
	}
}
