package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, length = 255)
	private String token;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public PasswordResetToken() {
	}

	public PasswordResetToken(String email, String token, LocalDateTime createdAt) {
		this.email = java.util.Objects.requireNonNull(email);
		this.token = java.util.Objects.requireNonNull(token);
		this.createdAt = createdAt;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
