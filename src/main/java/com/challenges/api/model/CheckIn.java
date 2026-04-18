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
import java.util.Objects;

@Entity
@Table(name = "check_ins")
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
	private SubTask subTask;

	protected CheckIn() {
	}

	public CheckIn(User user, Challenge challenge, LocalDate checkDate, SubTask subTask) {
		this.user = user;
		this.challenge = challenge;
		this.checkDate = checkDate;
		this.subTask = subTask;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public LocalDate getCheckDate() {
		return checkDate;
	}

	public SubTask getSubTask() {
		return subTask;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public void setChallenge(Challenge challenge) {
		this.challenge = challenge;
	}

	public void setCheckDate(LocalDate checkDate) {
		this.checkDate = java.util.Objects.requireNonNull(checkDate);
	}

	public void setSubTask(SubTask subTask) {
		this.subTask = subTask;
	}
}