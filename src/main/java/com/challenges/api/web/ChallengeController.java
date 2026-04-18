package com.challenges.api.web;

import com.challenges.api.model.Challenge;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.ChallengeRequest;
import com.challenges.api.web.dto.ChallengeResponse;
import jakarta.validation.Valid;
import java.util.List;
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

	private final UserRepository users;
	private final ChallengeRepository challenges;

	public ChallengeController(UserRepository users, ChallengeRepository challenges) {
		this.users = users;
		this.challenges = challenges;
	}

	@GetMapping
	public List<ChallengeResponse> list() {
		return challenges.findAll().stream().map(ChallengeResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<ChallengeResponse> get(@PathVariable Long id) {
		return challenges.findById(id)
				.map(ChallengeResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<ChallengeResponse> create(@Valid @RequestBody ChallengeRequest req) {
		return users.findById(req.ownerUserId())
				.map(owner -> {
					Challenge ch =
							new Challenge(owner, req.title(), req.description(), req.startDate(), req.endDate());
					return ResponseEntity.status(HttpStatus.CREATED)
							.body(ChallengeResponse.from(challenges.save(ch)));
				})
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<ChallengeResponse> replace(@PathVariable Long id, @Valid @RequestBody ChallengeRequest req) {
		return users.findById(req.ownerUserId())
				.flatMap(owner -> challenges.findById(id).map(ch -> {
					ch.setOwner(owner);
					ch.setTitle(req.title());
					ch.setDescription(req.description());
					ch.setStartDate(req.startDate());
					ch.setEndDate(req.endDate());
					return challenges.save(ch);
				}))
				.map(saved -> ResponseEntity.ok(ChallengeResponse.from(saved)))
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!challenges.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		challenges.deleteById(id);
		return ResponseEntity.noContent().build();
	}
}
