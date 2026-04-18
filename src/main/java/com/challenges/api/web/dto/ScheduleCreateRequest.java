package com.challenges.api.web.dto;

import com.challenges.api.model.ScheduleKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ScheduleCreateRequest(
		@Nullable Long challengeId,
		@Nullable Long subTaskId,
		@NotNull ScheduleKind kind,
		@Nullable List<String> weekDays) {

	@AssertTrue(message = "Exactly one of challengeId or subTaskId must be set")
	public boolean isExactlyOneOwner() {
		return (challengeId != null) ^ (subTaskId != null);
	}
}
