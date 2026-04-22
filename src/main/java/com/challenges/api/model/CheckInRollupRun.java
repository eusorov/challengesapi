package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "check_in_rollup_runs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckInRollupRun {

	@Id
	@Column(name = "challenge_id")
	private Long challengeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RollupStatus status;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public CheckInRollupRun(Long challengeId, RollupStatus status) {
		this.challengeId = challengeId;
		this.status = status;
	}

	@PrePersist
	@PreUpdate
	void touchUpdatedAt() {
		this.updatedAt = Instant.now();
	}
}
