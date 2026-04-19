package com.challenges.api.model;

/**
 * Fixed catalog of challenge categories (20). Persisted as enum {@linkplain jakarta.persistence.EnumType#STRING name}.
 */
public enum ChallengeCategory {
	HEALTH_AND_FITNESS,
	NUTRITION,
	MENTAL_WELLBEING,
	PRODUCTIVITY,
	LEARNING,
	CAREER,
	FINANCE,
	RELATIONSHIPS,
	CREATIVITY,
	SPORTS,
	OUTDOOR,
	READING,
	MEDITATION,
	SLEEP,
	SUSTAINABILITY,
	HOME,
	FAMILY,
	HOBBY,
	SOCIAL,
	OTHER
}
