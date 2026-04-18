package com.challenges.api.web.dto;

import com.challenges.api.model.ScheduleKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScheduleCreateRequest(
		Long challengeId,
		Long subTaskId,
		@NotNull ScheduleKind kind,
		List<String> weekDays) {

	@AssertTrue(message = "Exactly one of challengeId or subTaskId must be set")
	public boolean isExactlyOneOwner() {
		return (challengeId != null) ^ (subTaskId != null);
	}
}
