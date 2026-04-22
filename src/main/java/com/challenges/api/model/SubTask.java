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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subtasks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

	public SubTask(Challenge challenge, String title, int sortIndex) {
		this.challenge = challenge;
		this.title = title;
		this.sortIndex = sortIndex;
	}
}
