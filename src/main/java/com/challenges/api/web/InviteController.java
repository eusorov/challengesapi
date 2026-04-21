package com.challenges.api.web;

import com.authspring.api.security.UserPrincipal;
import com.challenges.api.service.InviteService;
import com.challenges.api.web.dto.InviteListRole;
import com.challenges.api.web.dto.InviteCreateRequest;
import com.challenges.api.web.dto.InviteResponse;
import com.challenges.api.web.dto.InviteUpdateRequest;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/invites", version = "1")
public class InviteController {

	private final InviteService inviteService;

	public InviteController(InviteService inviteService) {
		this.inviteService = inviteService;
	}

	@GetMapping({ "", "/" })
	public ResponseEntity<Page<InviteResponse>> list(
			@RequestParam(required = false) @Nullable Long challengeId,
			@Parameter(description = "RECEIVED = invites where you are the invitee (default); SENT = invites you created")
			@RequestParam(defaultValue = "RECEIVED")
					InviteListRole role,
			@PageableDefault(size = 20) Pageable pageable,
			@AuthenticationPrincipal UserPrincipal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Page<InviteResponse> page =
				inviteService.listForUser(principal.getId(), role, challengeId, pageable).map(InviteResponse::from);
		return ResponseEntity.ok(page);
	}

	@GetMapping({ "/{id}", "/{id}/" })
	public ResponseEntity<InviteResponse> get(@PathVariable Long id) {
		return inviteService.findById(id)
				.map(InviteResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "", "/" })
	public ResponseEntity<InviteResponse> create(
			@Valid @RequestBody InviteCreateRequest req, @AuthenticationPrincipal UserPrincipal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return inviteService
				.createForAuthenticatedInviter(principal.getId(), req)
				.map(inv -> ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(inv)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping({ "/{id}", "/{id}/" })
	public ResponseEntity<InviteResponse> update(
			@PathVariable Long id, @Valid @RequestBody InviteUpdateRequest req) {
		return inviteService.update(id, req)
				.map(InviteResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping({ "/{id}", "/{id}/" })
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!inviteService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
