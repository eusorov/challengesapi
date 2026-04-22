package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ScheduleRepository;
import com.challenges.api.repo.SubTaskRepository;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class ScheduleService {

	private final ScheduleRepository schedules;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public ScheduleService(ScheduleRepository schedules, ChallengeRepository challenges, SubTaskRepository subTasks) {
		this.schedules = schedules;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	public static @NonNull List<DayOfWeek> parseWeekDays(@Nullable List<String> raw) {
		if (raw == null || raw.isEmpty()) {
			return Objects.requireNonNull(Collections.emptyList());
		}
		return Objects.requireNonNull(
				raw.stream().map(String::trim).map(String::toUpperCase).map(DayOfWeek::valueOf).toList());
	}

	@Transactional
	public Optional<Schedule> createForChallenge(
			@NonNull Long challengeId, @NonNull ScheduleKind kind, @NonNull List<DayOfWeek> weekDays) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(kind, "kind must not be null");
		Assert.notNull(weekDays, "weekDays must not be null");
		return challenges.findById(challengeId).map(ch -> replaceChallengeSchedule(ch, kind, weekDays));
	}

	@Transactional
	public Optional<Schedule> createForSubTask(
			@NonNull Long subTaskId, @NonNull ScheduleKind kind, @NonNull List<DayOfWeek> weekDays) {
		Assert.notNull(subTaskId, "subTaskId must not be null");
		Assert.notNull(kind, "kind must not be null");
		Assert.notNull(weekDays, "weekDays must not be null");
		return subTasks.findById(subTaskId).map(st -> replaceSubTaskSchedule(st, kind, weekDays));
	}

	private Schedule replaceChallengeSchedule(Challenge ch, ScheduleKind kind, List<DayOfWeek> weekDays) {
		Schedule existing = ch.getSchedule();
		if (existing != null) {
			ch.setSchedule(null);
			schedules.delete(existing);
		}
		Schedule s = Schedule.forChallenge(ch, kind, weekDays);
		ch.setSchedule(s);
		return Objects.requireNonNull(schedules.save(s));
	}

	private Schedule replaceSubTaskSchedule(SubTask st, ScheduleKind kind, List<DayOfWeek> weekDays) {
		Schedule existing = st.getSchedule();
		if (existing != null) {
			st.setSchedule(null);
			schedules.delete(existing);
		}
		Schedule s = Schedule.forSubTask(st, kind, weekDays);
		st.setSchedule(s);
		return Objects.requireNonNull(schedules.save(s));
	}

	@Transactional(readOnly = true)
	public Optional<Schedule> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return schedules.findByIdWithAssociations(id);
	}

	@Transactional
	public Optional<Schedule> update(
			@NonNull Long id, @NonNull ScheduleKind kind, @NonNull List<DayOfWeek> weekDays) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(kind, "kind must not be null");
		Assert.notNull(weekDays, "weekDays must not be null");
		return schedules.findByIdWithAssociations(id).map(s -> {
			s.setKind(kind);
			s.replaceWeekDays(weekDays);
			return Objects.requireNonNull(schedules.save(s));
		});
	}

	@Transactional
	public boolean delete(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return schedules.findByIdWithAssociations(id).map(s -> {
			if (s.getChallenge() != null) {
				s.getChallenge().setSchedule(null);
			}
			if (s.getSubTask() != null) {
				s.getSubTask().setSchedule(null);
			}
			schedules.delete(s);
			return true;
		}).orElse(false);
	}
}
