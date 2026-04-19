package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, length = 255)
	private String token;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public PasswordResetToken() {
	}

	public PasswordResetToken(String email, String token, Instant createdAt) {
		this.email = java.util.Objects.requireNonNull(email);
		this.token = java.util.Objects.requireNonNull(token);
		this.createdAt = java.util.Objects.requireNonNull(createdAt);
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = java.util.Objects.requireNonNull(email);
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = java.util.Objects.requireNonNull(token);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = java.util.Objects.requireNonNull(createdAt);
	}
}
