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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	public static final String DEFAULT_ROLE = "USER";

	/** BCrypt hash of "password" — for tests and API-created users until auth sets real passwords. */
	public static final String TEST_PASSWORD_HASH =
			"$2a$10$vl9H0JyB3fK6UESudJxbweJu8m.SeqoLrxjRMpxGmuFetUc2Qtyme";

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
}
