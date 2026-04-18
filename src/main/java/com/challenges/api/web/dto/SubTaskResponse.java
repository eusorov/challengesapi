package com.challenges.api.web.dto;

import com.challenges.api.model.SubTask;

public record SubTaskResponse(Long id, Long challengeId, String title, int sortIndex) {

	public static SubTaskResponse from(SubTask st) {
		return new SubTaskResponse(st.getId(), st.getChallenge().getId(), st.getTitle(), st.getSortIndex());
	}
}
