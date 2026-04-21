package com.challenges.api.web;

import com.authspring.api.security.UserPrincipal;
import com.challenges.api.service.CheckInService;
import com.challenges.api.web.dto.CheckInRequest;
import com.challenges.api.web.dto.CheckInResponse;
import com.challenges.api.web.dto.CheckInUpdateRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class CheckInController {

	private final CheckInService checkInService;

	public CheckInController(CheckInService checkInService) {
		this.checkInService = checkInService;
	}

	@GetMapping({
		"/challenges/{challengeId:\\d+}/check-ins",
		"/challenges/{challengeId:\\d+}/check-ins/"
	})
	public @NonNull Page<CheckInResponse> listForChallenge(
			@PathVariable Long challengeId,
			@AuthenticationPrincipal @Nullable UserPrincipal principal,
			@PageableDefault(size = 20) Pageable pageable) {
		Long viewerId = principal != null ? principal.getId() : null;
		return checkInService.listForChallenge(challengeId, viewerId, pageable).map(CheckInResponse::from);
	}

	@GetMapping({ "/check-ins/{id}", "/check-ins/{id}/" })
	public ResponseEntity<CheckInResponse> get(
			@PathVariable Long id, @AuthenticationPrincipal @Nullable UserPrincipal principal) {
		Long viewerId = principal != null ? principal.getId() : null;
		return checkInService.findByIdForViewer(id, viewerId)
				.map(CheckInResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "/check-ins", "/check-ins/" })
	public ResponseEntity<CheckInResponse> create(
			@Valid @RequestBody CheckInRequest req, @AuthenticationPrincipal @Nullable UserPrincipal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!req.userId().equals(principal.getId())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		return checkInService.create(req, principal.getId())
				.map(c -> ResponseEntity.status(HttpStatus.CREATED).body(CheckInResponse.from(c)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping({ "/check-ins/{id}", "/check-ins/{id}/" })
	public ResponseEntity<CheckInResponse> replace(
			@PathVariable Long id,
			@Valid @RequestBody CheckInUpdateRequest req,
			@AuthenticationPrincipal @Nullable UserPrincipal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return checkInService.replace(id, req, principal.getId())
				.map(CheckInResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping({ "/check-ins/{id}", "/check-ins/{id}/" })
	public ResponseEntity<Void> delete(
			@PathVariable Long id, @AuthenticationPrincipal @Nullable UserPrincipal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!checkInService.delete(id, principal.getId())) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
