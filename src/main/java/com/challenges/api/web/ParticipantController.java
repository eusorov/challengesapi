package com.challenges.api.web;

import com.authspring.api.security.UserPrincipal;
import com.challenges.api.service.ParticipantService;
import com.challenges.api.web.dto.ParticipantResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/challenges", version = "1")
public class ParticipantController {

	private final ParticipantService participantService;

	public ParticipantController(ParticipantService participantService) {
		this.participantService = participantService;
	}

	@GetMapping({ "/{challengeId:\\d+}/participants", "/{challengeId:\\d+}/participants/" })
	public @NonNull Page<ParticipantResponse> listForChallenge(
			@PathVariable Long challengeId,
			@AuthenticationPrincipal @Nullable UserPrincipal principal,
			@PageableDefault(size = 20) Pageable pageable) {
		Long viewerId = principal != null ? principal.getId() : null;
		return participantService
				.listForChallengeIfVisible(challengeId, viewerId, pageable)
				.map(ParticipantResponse::from);
	}
}
