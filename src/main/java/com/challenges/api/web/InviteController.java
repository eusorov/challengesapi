package com.challenges.api.web;

import com.challenges.api.model.Invite;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.InviteRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
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

	private final InviteRepository invites;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public InviteController(
			InviteRepository invites,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks) {
		this.invites = invites;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@GetMapping
	public List<InviteResponse> list(@RequestParam(required = false) Long challengeId) {
		if (challengeId != null) {
			return invites.findByChallenge_Id(challengeId).stream().map(InviteResponse::from).toList();
		}
		return invites.findAll().stream().map(InviteResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<InviteResponse> get(@PathVariable Long id) {
		return invites.findById(id)
				.map(InviteResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<InviteResponse> create(@Valid @RequestBody InviteRequest req) {
		var inviter = users.findById(req.inviterUserId());
		var invitee = users.findById(req.inviteeUserId());
		var challenge = challenges.findById(req.challengeId());
		if (inviter.isEmpty() || invitee.isEmpty() || challenge.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		SubTask st = null;
		if (req.subTaskId() != null) {
			var ost = subTasks.findById(req.subTaskId());
			if (ost.isEmpty()) {
				return ResponseEntity.notFound().build();
			}
			st = ost.get();
			if (!st.getChallenge().getId().equals(challenge.get().getId())) {
				throw new IllegalStateException("subTask must belong to the challenge");
			}
		}
		Invite inv = new Invite(inviter.get(), invitee.get(), challenge.get(), st);
		if (req.status() != null) {
			inv.setStatus(req.status());
		}
		if (req.expiresAt() != null) {
			inv.setExpiresAt(req.expiresAt());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.from(invites.save(inv)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<InviteResponse> update(
			@PathVariable Long id, @Valid @RequestBody InviteUpdateRequest req) {
		return invites.findById(id)
				.map(inv -> {
					if (req.status() != null) {
						inv.setStatus(req.status());
					}
					if (req.expiresAt() != null) {
						inv.setExpiresAt(req.expiresAt());
					}
					return ResponseEntity.ok(InviteResponse.from(invites.save(inv)));
				})
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!invites.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		invites.deleteById(id);
		return ResponseEntity.noContent().build();
	}
}
