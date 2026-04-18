package com.challenges.api.web.dto;

import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import java.time.DayOfWeek;
import java.util.List;

public record ScheduleResponse(
		Long id,
		Long challengeId,
		Long subTaskId,
		ScheduleKind kind,
		List<String> weekDays) {

	public static ScheduleResponse from(Schedule s) {
		Long chId = s.getChallenge() != null ? s.getChallenge().getId() : null;
		Long stId = s.getSubTask() != null ? s.getSubTask().getId() : null;
		List<String> days = s.getWeekDays().stream().map(DayOfWeek::name).toList();
		return new ScheduleResponse(s.getId(), chId, stId, s.getKind(), days);
	}
}
