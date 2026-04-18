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
@Table(name = "comments")
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_user_id", nullable = false)
	private User author;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	/**
	 * {@code null} = comment on the whole challenge; non-null = comment on this subtask (must belong to
	 * {@link #challenge}).
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Column(nullable = false, length = 8000)
	private String body;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected Comment() {
	}

	/** Challenge-wide comment (not tied to a single subtask). */
	public Comment(User author, Challenge challenge, String body) {
		this.author = Objects.requireNonNull(author);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = null;
		this.body = Objects.requireNonNull(body);
	}

	/** Comment scoped to a subtask (must belong to {@code challenge}). */
	public Comment(User author, Challenge challenge, SubTask subTask, String body) {
		this.author = Objects.requireNonNull(author);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = Objects.requireNonNull(subTask);
		this.body = Objects.requireNonNull(body);
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

	public User getAuthor() {
		return author;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public SubTask getSubTask() {
		return subTask;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setBody(String body) {
		this.body = Objects.requireNonNull(body);
	}
}
