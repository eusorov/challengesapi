package com.challenges.api.web;

import com.challenges.api.service.ChallengeService;
import com.challenges.api.web.dto.ChallengeRequest;
import com.challenges.api.web.dto.ChallengeResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/challenges", version = "1")
public class ChallengeController {

	private final ChallengeService challengeService;

	public ChallengeController(ChallengeService challengeService) {
		this.challengeService = challengeService;
	}

	@GetMapping
	public @NonNull List<ChallengeResponse> list() {
		return challengeService.listChallenges().stream().map(ChallengeResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<ChallengeResponse> get(@PathVariable Long id) {
		return challengeService.findById(id)
				.map(ChallengeResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<ChallengeResponse> create(@Valid @RequestBody ChallengeRequest req) {
		return challengeService.create(req)
				.map(ch -> ResponseEntity.status(HttpStatus.CREATED).body(ChallengeResponse.from(ch)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<ChallengeResponse> replace(@PathVariable Long id, @Valid @RequestBody ChallengeRequest req) {
		return challengeService.replace(id, req)
				.map(ChallengeResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!challengeService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
