package com.challenges.api.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Setter(AccessLevel.NONE)
	private Long id;

	/** When set, this schedule belongs to the challenge (subtask must be null). */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "challenge_id", unique = true)
	@Setter(AccessLevel.NONE)
	private Challenge challenge;

	/** When set, this schedule belongs to the subtask (challenge must be null). */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id", unique = true)
	@Setter(AccessLevel.NONE)
	private SubTask subTask;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	@Setter
	private ScheduleKind kind = ScheduleKind.DAILY;

	@ElementCollection
	@Enumerated(EnumType.STRING)
	@CollectionTable(name = "schedule_weekdays", joinColumns = @JoinColumn(name = "schedule_id"))
	@Column(name = "day_of_week", nullable = false, length = 16)
	@Setter(AccessLevel.NONE)
	private List<DayOfWeek> weekDays = new ArrayList<>();

	/** Schedule for a challenge (not for a subtask). */
	public static Schedule forChallenge(Challenge challenge, ScheduleKind kind, List<DayOfWeek> weekDays) {
		Schedule s = new Schedule();
		s.challenge = challenge;
		s.subTask = null;
		s.kind = kind;
		if (weekDays != null) {
			s.weekDays = new ArrayList<>(weekDays);
		}
		return s;
	}

	/** Schedule for a subtask (not stored on the challenge row). */
	public static Schedule forSubTask(SubTask subTask, ScheduleKind kind, List<DayOfWeek> weekDays) {
		Schedule s = new Schedule();
		s.subTask = subTask;
		s.challenge = null;
		s.kind = kind;
		if (weekDays != null) {
			s.weekDays = new ArrayList<>(weekDays);
		}
		return s;
	}

	@PrePersist
	@PreUpdate
	private void validateExactlyOneOwner() {
		boolean hasC = challenge != null;
		boolean hasS = subTask != null;
		if (hasC == hasS) {
			throw new IllegalStateException("Schedule must have exactly one of challenge or subTask");
		}
	}

	public void replaceWeekDays(List<DayOfWeek> days) {
		this.weekDays.clear();
		if (days != null) {
			this.weekDays.addAll(days);
		}
	}
}
