package com.challenges.api.support;

/** Business rules for {@link com.challenges.api.model.Challenge} aggregates. */
public final class ChallengeConstraints {

	private ChallengeConstraints() {}

	/** Maximum number of subtasks allowed per challenge (inclusive bound: at most this many rows). */
	public static final int MAX_SUBTASKS_PER_CHALLENGE = 10;
}
