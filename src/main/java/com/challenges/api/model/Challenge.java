package com.challenges.api.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.locationtech.jts.geom.Point;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@NoArgsConstructor
public class Challenge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_user_id", nullable = false)
	private User owner;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(length = 8000)
	private @Nullable String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private ChallengeCategory category;

	/** Inclusive start of the challenge window. */
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Inclusive end of the challenge window, or {@code null} for an open-ended challenge. When non-null, must not be before {@link #startDate}. */
	@Column(name = "end_date")
	private @Nullable LocalDate endDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@OneToOne(mappedBy = "challenge", cascade = CascadeType.ALL, optional = true)
	private @Nullable Schedule schedule;

	@OneToMany(mappedBy = "challenge", fetch = FetchType.LAZY)
	@OrderBy("sortIndex ASC")
	private List<SubTask> subtasks = new ArrayList<>();

	@Column(name = "image_object_key", length = 1024)
	private @Nullable String imageObjectKey;

	@Column(name = "is_private", nullable = false)
	private boolean isPrivate = false;

	@Column(length = 255)
	private @Nullable String city;

	@Column(columnDefinition = "geography(Point,4326)")
	private @Nullable Point location;

	public Challenge(
			User owner,
			String title,
			@Nullable String description,
			LocalDate startDate,
			@Nullable LocalDate endDate,
			ChallengeCategory category,
			@Nullable String city,
			@Nullable Point location,
			boolean isPrivate) {
		this.owner = owner;
		this.title = title;
		this.description = description;
		this.startDate = startDate;
		this.endDate = endDate;
		this.category = java.util.Objects.requireNonNull(category);
		this.isPrivate = isPrivate;
		this.city = city;
		this.location = location;
	}

	@PrePersist
	@PreUpdate
	private void validateDateRange() {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalStateException("endDate must not be before startDate");
		}
	}

}
