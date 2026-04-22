package com.challenges.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "personal_access_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PersonalAccessToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tokenable_type", nullable = false, length = 255)
	private String tokenableType;

	@Column(name = "tokenable_id", nullable = false)
	private Long tokenableId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String name;

	@Column(nullable = false, unique = true, length = 64)
	private String token;

	@Column(columnDefinition = "TEXT")
	private String abilities;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
