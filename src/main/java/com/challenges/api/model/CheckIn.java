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
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "check_ins")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckIn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	@Column(name = "check_date", nullable = false)
	private LocalDate checkDate;

	/** When {@code null}, check-in is for the challenge as a whole; when set, check-in is for this subtask (must belong to {@link #challenge}). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private @Nullable SubTask subTask;

	public CheckIn(User user, Challenge challenge, LocalDate checkDate, SubTask subTask) {
		this.user = user;
		this.challenge = challenge;
		this.checkDate = checkDate;
		this.subTask = subTask;
	}
}