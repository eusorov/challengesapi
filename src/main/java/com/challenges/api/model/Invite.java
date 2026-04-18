package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "invites")
public class Invite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inviter_id", nullable = false)
	private User inviter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invitee_id", nullable = false)
	private User invitee;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	/** {@code null} = invite to whole challenge; non-null = invite scoped to this subtask. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subtask_id")
	private SubTask subTask;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private InviteStatus status = InviteStatus.PENDING;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "expires_at")
	private Instant expiresAt;

	protected Invite() {
	}

	public Invite(User inviter, User invitee, Challenge challenge, SubTask subTask) {
		this.inviter = Objects.requireNonNull(inviter);
		this.invitee = Objects.requireNonNull(invitee);
		this.challenge = Objects.requireNonNull(challenge);
		this.subTask = subTask;
	}

	/** Challenge-wide invite (no subtask scope). */
	public Invite(User inviter, User invitee, Challenge challenge) {
		this(inviter, invitee, challenge, null);
	}

	@PrePersist
	@PreUpdate
	private void validate() {
		if (inviter.getId() != null && invitee.getId() != null && inviter.getId().equals(invitee.getId())) {
			throw new IllegalStateException("inviter and invitee must differ");
		}
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

	public User getInviter() {
		return inviter;
	}

	public User getInvitee() {
		return invitee;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public SubTask getSubTask() {
		return subTask;
	}

	public InviteStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setStatus(InviteStatus status) {
		this.status = Objects.requireNonNull(status);
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}
}
