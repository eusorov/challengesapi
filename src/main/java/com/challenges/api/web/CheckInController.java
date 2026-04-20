package com.challenges.api.web;

import com.challenges.api.service.CheckInService;
import com.challenges.api.web.dto.CheckInRequest;
import com.challenges.api.web.dto.CheckInResponse;
import com.challenges.api.web.dto.CheckInUpdateRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
			@PathVariable Long challengeId, @PageableDefault(size = 20) Pageable pageable) {
		return checkInService.listForChallenge(challengeId, pageable).map(CheckInResponse::from);
	}

	@GetMapping({ "/check-ins/{id}", "/check-ins/{id}/" })
	public ResponseEntity<CheckInResponse> get(@PathVariable Long id) {
		return checkInService.findById(id)
				.map(CheckInResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "/check-ins", "/check-ins/" })
	public ResponseEntity<CheckInResponse> create(@Valid @RequestBody CheckInRequest req) {
		return checkInService.create(req)
				.map(c -> ResponseEntity.status(HttpStatus.CREATED).body(CheckInResponse.from(c)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping({ "/check-ins/{id}", "/check-ins/{id}/" })
	public ResponseEntity<CheckInResponse> replace(
			@PathVariable Long id, @Valid @RequestBody CheckInUpdateRequest req) {
		return checkInService.replace(id, req)
				.map(CheckInResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping({ "/check-ins/{id}", "/check-ins/{id}/" })
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!checkInService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
