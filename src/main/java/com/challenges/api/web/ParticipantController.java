package com.challenges.api.web;

import com.challenges.api.service.ParticipantService;
import com.challenges.api.web.dto.ParticipantResponse;
import java.util.List;
import org.jspecify.annotations.NonNull;
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

	@GetMapping("/{challengeId}/participants")
	public @NonNull List<ParticipantResponse> listForChallenge(@PathVariable Long challengeId) {
		return participantService.listForChallenge(challengeId).stream()
				.map(ParticipantResponse::from)
				.toList();
	}
}
