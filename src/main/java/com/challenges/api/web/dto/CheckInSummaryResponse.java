package com.challenges.api.web.dto;

import com.challenges.api.model.CheckInSummary;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record CheckInSummaryResponse(
		Long userId,
		Long challengeId,
		@Nullable Long subTaskId,
		long totalCheckIns,
		LocalDate firstCheckInDate,
		LocalDate lastCheckInDate,
		Instant rolledUpAt) {

	public static CheckInSummaryResponse from(CheckInSummary s) {
		Long st = s.getSubTask() != null ? s.getSubTask().getId() : null;
		return new CheckInSummaryResponse(
				s.getUser().getId(),
				s.getChallenge().getId(),
				st,
				s.getTotalCheckIns(),
				s.getFirstCheckInDate(),
				s.getLastCheckInDate(),
				s.getRolledUpAt());
	}
}
