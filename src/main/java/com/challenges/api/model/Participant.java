package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "participants")
public class Participant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	/** {@code null} = membership for the entire challenge; non-null = membership scoped to this subtask only. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Column(nullable = false, updatable = false)
	private Instant joinedAt = Instant.now();

	protected Participant() {
	}

	/** Challenge-wide participation (not tied to a single subtask). */
	public Participant(User user, Challenge challenge) {
		this.user = Objects.requireNonNull(user);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = null;
	}

	/** Participation scoped to one subtask (must belong to {@code challenge}). */
	public Participant(User user, Challenge challenge, SubTask subTask) {
		this.user = Objects.requireNonNull(user);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = Objects.requireNonNull(subTask);
	}

	@PrePersist
	@PreUpdate
	private void validateSubTaskBelongsToChallenge() {
		if (subTask == null) {
			return;
		}
		if (subTask.getChallenge().getId() == null || challenge.getId() == null) {
			return;
		}
		if (!subTask.getChallenge().getId().equals(challenge.getId())) {
			throw new IllegalStateException("subTask must belong to the same challenge");
		}
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

	public SubTask getSubTask() {
		return subTask;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
