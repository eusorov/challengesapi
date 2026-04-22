package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "check_in_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckInSummary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Column(name = "total_check_ins", nullable = false)
	private long totalCheckIns;

	@Column(name = "first_check_in_date", nullable = false)
	private LocalDate firstCheckInDate;

	@Column(name = "last_check_in_date", nullable = false)
	private LocalDate lastCheckInDate;

	@Column(name = "rolled_up_at", nullable = false)
	private Instant rolledUpAt = Instant.now();

	public CheckInSummary(
			User user,
			Challenge challenge,
			SubTask subTask,
			long totalCheckIns,
			LocalDate firstCheckInDate,
			LocalDate lastCheckInDate,
			Instant rolledUpAt) {
		this.user = user;
		this.challenge = challenge;
		this.subTask = subTask;
		this.totalCheckIns = totalCheckIns;
		this.firstCheckInDate = firstCheckInDate;
		this.lastCheckInDate = lastCheckInDate;
		this.rolledUpAt = rolledUpAt;
	}
}
