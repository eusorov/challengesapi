package com.challenges.api.web;

import com.challenges.api.service.InviteService;
import com.challenges.api.web.dto.InviteRequest;
import com.challenges.api.web.dto.InviteResponse;
import com.challenges.api.web.dto.InviteUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/invites", version = "1")
public class InviteController {

	private final InviteService inviteService;

	public InviteController(InviteService inviteService) {
		this.inviteService = inviteService;
	}

	@GetMapping
	public List<InviteResponse> list(@RequestParam(required = false) Long challengeId) {
		return inviteService.list(challengeId).stream().map(InviteResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<InviteResponse> get(@PathVariable Long id) {
		return inviteService.findById(id)
				.map(InviteResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<InviteResponse> create(@Valid @RequestBody InviteRequest req) {
		return inviteService.create(req)
				.map(inv -> ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(inv)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<InviteResponse> update(
			@PathVariable Long id, @Valid @RequestBody InviteUpdateRequest req) {
		return inviteService.update(id, req)
				.map(InviteResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!inviteService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
