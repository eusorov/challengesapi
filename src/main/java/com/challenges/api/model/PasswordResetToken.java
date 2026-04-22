package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

	@Id
	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, length = 255)
	private String token;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public PasswordResetToken(String email, String token, Instant createdAt) {
		this.email = java.util.Objects.requireNonNull(email);
		this.token = java.util.Objects.requireNonNull(token);
		this.createdAt = java.util.Objects.requireNonNull(createdAt);
	}
}
