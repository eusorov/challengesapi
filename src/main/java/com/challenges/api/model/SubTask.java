package com.challenges.api.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "subtasks")
public class SubTask {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenge_id", nullable = false)
	private Challenge challenge;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(name = "sort_index", nullable = false)
	private int sortIndex;

	@OneToOne(mappedBy = "subTask", cascade = CascadeType.ALL, optional = true)
	private Schedule schedule;

	protected SubTask() {
	}

	public SubTask(Challenge challenge, String title, int sortIndex) {
		this.challenge = challenge;
		this.title = title;
		this.sortIndex = sortIndex;
	}

	public Long getId() {
		return id;
	}

	public Challenge getChallenge() {
		return challenge;
	}

	public String getTitle() {
		return title;
	}

	public int getSortIndex() {
		return sortIndex;
	}

	public Schedule getSchedule() {
		return schedule;
	}

	public void bindSchedule(Schedule s) {
		this.schedule = s;
	}

	public void setTitle(String title) {
		this.title = java.util.Objects.requireNonNull(title);
	}

	public void setSortIndex(int sortIndex) {
		this.sortIndex = sortIndex;
	}
}
