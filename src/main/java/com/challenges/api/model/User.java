package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

	public static final String DEFAULT_ROLE = "ROLE_USER";

	/** BCrypt hash of "password" — for tests and API-created users until auth sets real passwords. */
	public static final String TEST_PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "date_closed")
	private LocalDate dateClosed;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(nullable = false, length = 50)
	private String role;

	@Column(name = "remember_token", length = 100)
	private String rememberToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String name, String email, String password, String role) {
		this.name = java.util.Objects.requireNonNull(name);
		this.email = java.util.Objects.requireNonNull(email);
		this.password = java.util.Objects.requireNonNull(password);
		this.role = java.util.Objects.requireNonNull(role);
	}

	/** Minimal user for tests: name "User", role "user", known password hash. */
	public static User forTest(String email) {
		return new User("User", email, TEST_PASSWORD_HASH, DEFAULT_ROLE);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public void setEmailVerifiedAt(Instant emailVerifiedAt) {
		this.emailVerifiedAt = emailVerifiedAt;
	}

	public LocalDate getDateClosed() {
		return dateClosed;
	}

	public void setDateClosed(LocalDate dateClosed) {
		this.dateClosed = dateClosed;
	}

	public String getPassword() {
		return password;
	}

	public String getRole() {
		return role;
	}

	public String getRememberToken() {
		return rememberToken;
	}

	public void setRememberToken(String rememberToken) {
		this.rememberToken = rememberToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setEmail(String email) {
		this.email = java.util.Objects.requireNonNull(email);
	}

	public void setName(String name) {
		this.name = java.util.Objects.requireNonNull(name);
	}

	public void setPassword(String password) {
		this.password = java.util.Objects.requireNonNull(password);
	}

	public void setRole(String role) {
		this.role = java.util.Objects.requireNonNull(role);
	}
}
