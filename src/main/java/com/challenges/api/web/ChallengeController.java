package com.challenges.api.web;

import com.authspring.api.security.UserPrincipal;
import com.challenges.api.service.ChallengeService;
import com.challenges.api.service.JoinChallengeOutcome;
import com.challenges.api.service.ParticipantService;
import com.challenges.api.web.dto.ChallengeRequest;
import com.challenges.api.web.dto.ChallengeResponse;
import com.challenges.api.web.dto.ParticipantResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/challenges", version = "1")
public class ChallengeController {

	private final ChallengeService challengeService;
	private final ParticipantService participantService;
	private final String imagePublicBaseUrl;

	public ChallengeController(
			ChallengeService challengeService,
			ParticipantService participantService,
			@Value("${aws.s3.public-base-url:}") String imagePublicBaseUrl) {
		this.challengeService = challengeService;
		this.participantService = participantService;
		this.imagePublicBaseUrl = imagePublicBaseUrl;
	}

	@GetMapping({ "", "/" })
	public @NonNull Page<ChallengeResponse> list(@PageableDefault(size = 20) Pageable pageable) {
		return challengeService
				.listChallenges(pageable)
				.map(ch -> ChallengeResponse.from(ch, imagePublicBaseUrl));
	}

	@GetMapping({ "/{id:\\d+}", "/{id:\\d+}/" })
	public ResponseEntity<ChallengeResponse> get(@PathVariable Long id) {
		return challengeService.findById(id)
				.map(ch -> ChallengeResponse.from(ch, imagePublicBaseUrl))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "", "/" })
	public ResponseEntity<ChallengeResponse> create(@Valid @RequestBody ChallengeRequest req) {
		return challengeService.create(req)
				.map(ch -> ResponseEntity.status(HttpStatus.CREATED)
						.body(ChallengeResponse.from(ch, imagePublicBaseUrl)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "/{id:\\d+}/join", "/{id:\\d+}/join/" })
	public ResponseEntity<ParticipantResponse> join(
			@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		JoinChallengeOutcome outcome = participantService.joinChallenge(id, principal.getId());
		ParticipantResponse body = ParticipantResponse.from(outcome.participant());
		if (outcome.created()) {
			return ResponseEntity.status(HttpStatus.CREATED).body(body);
		}
		return ResponseEntity.ok(body);
	}

	@PutMapping({ "/{id:\\d+}", "/{id:\\d+}/" })
	public ResponseEntity<ChallengeResponse> replace(@PathVariable Long id, @Valid @RequestBody ChallengeRequest req) {
		return challengeService.replace(id, req)
				.map(ch -> ChallengeResponse.from(ch, imagePublicBaseUrl))
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "/{id:\\d+}/image", "/{id:\\d+}/image/" })
	public ResponseEntity<ChallengeResponse> uploadImage(
			@PathVariable Long id,
			@RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal UserPrincipal principal) {
		return challengeService
				.uploadImage(id, file, principal.getId())
				.map(ch -> ResponseEntity.ok(ChallengeResponse.from(ch, imagePublicBaseUrl)))
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping({ "/{id:\\d+}", "/{id:\\d+}/" })
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!challengeService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
