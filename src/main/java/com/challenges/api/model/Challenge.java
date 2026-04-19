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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "challenges")
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
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 64)
	private ChallengeCategory category;

	/** Inclusive start of the challenge window. */
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Inclusive end of the challenge window, or {@code null} for an open-ended challenge. When non-null, must not be before {@link #startDate}. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@OneToOne(mappedBy = "challenge", cascade = CascadeType.ALL, optional = true)
	private Schedule schedule;

	@OneToMany(mappedBy = "challenge", fetch = FetchType.LAZY)
	@OrderBy("sortIndex ASC")
	private List<SubTask> subtasks = new ArrayList<>();

	@Column(name = "image_object_key", length = 1024)
	private String imageObjectKey;

	protected Challenge() {
	}

	public Challenge(
			User owner,
			String title,
			String description,
			LocalDate startDate,
			LocalDate endDate,
			ChallengeCategory category) {
		this.owner = owner;
		this.title = title;
		this.description = description;
		this.startDate = startDate;
		this.endDate = endDate;
		this.category = java.util.Objects.requireNonNull(category);
	}

	@PrePersist
	@PreUpdate
	private void validateDateRange() {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalStateException("endDate must not be before startDate");
		}
	}

	public Long getId() {
		return id;
	}

	public User getOwner() {
		return owner;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public ChallengeCategory getCategory() {
		return category;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Schedule getSchedule() {
		return schedule;
	}

	public List<SubTask> getSubtasks() {
		return subtasks;
	}

	public String getImageObjectKey() {
		return imageObjectKey;
	}

	public void setImageObjectKey(String imageObjectKey) {
		this.imageObjectKey = imageObjectKey;
	}

	public void bindSchedule(Schedule s) {
		this.schedule = s;
	}

	public void setOwner(User owner) {
		this.owner = java.util.Objects.requireNonNull(owner);
	}

	public void setTitle(String title) {
		this.title = java.util.Objects.requireNonNull(title);
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setCategory(ChallengeCategory category) {
		this.category = java.util.Objects.requireNonNull(category);
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = java.util.Objects.requireNonNull(startDate);
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}
}
