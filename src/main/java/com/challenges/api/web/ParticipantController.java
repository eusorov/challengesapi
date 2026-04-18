package com.challenges.api.web;

import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.web.dto.ParticipantResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/challenges", version = "1")
public class ParticipantController {

	private final ParticipantRepository participants;

	public ParticipantController(ParticipantRepository participants) {
		this.participants = participants;
	}

	@GetMapping("/{challengeId}/participants")
	public List<ParticipantResponse> listForChallenge(@PathVariable Long challengeId) {
		return participants.findByChallenge_Id(challengeId).stream()
				.map(ParticipantResponse::from)
				.toList();
	}
}
