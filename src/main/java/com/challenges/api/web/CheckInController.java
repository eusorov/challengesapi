package com.challenges.api.web;

import com.challenges.api.model.CheckIn;
import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.CheckInRequest;
import com.challenges.api.web.dto.CheckInResponse;
import com.challenges.api.web.dto.CheckInUpdateRequest;
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
@RequestMapping(path = "/api", version = "1")
public class CheckInController {

	private final CheckInRepository checkIns;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public CheckInController(
			CheckInRepository checkIns,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks) {
		this.checkIns = checkIns;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@GetMapping("/challenges/{challengeId}/check-ins")
	public List<CheckInResponse> listForChallenge(@PathVariable Long challengeId) {
		return checkIns.findByChallenge_IdOrderByCheckDateDesc(challengeId).stream()
				.map(CheckInResponse::from)
				.toList();
	}

	@GetMapping("/check-ins/{id}")
	public ResponseEntity<CheckInResponse> get(@PathVariable Long id) {
		return checkIns.findById(id)
				.map(CheckInResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/check-ins")
	public ResponseEntity<CheckInResponse> create(@Valid @RequestBody CheckInRequest req) {
		var user = users.findById(req.userId());
		var challenge = challenges.findById(req.challengeId());
		if (user.isEmpty() || challenge.isEmpty()) {
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
				throw new IllegalStateException("subTask must belong to the check-in challenge");
			}
		}
		CheckIn c = new CheckIn(user.get(), challenge.get(), req.checkDate(), st);
		return ResponseEntity.status(HttpStatus.CREATED).body(CheckInResponse.from(checkIns.save(c)));
	}

	@PutMapping("/check-ins/{id}")
	public ResponseEntity<CheckInResponse> replace(
			@PathVariable Long id, @Valid @RequestBody CheckInUpdateRequest req) {
		return checkIns.findById(id)
				.map(ci -> {
					if (req.subTaskId() != null) {
						SubTask st =
								subTasks.findById(req.subTaskId()).orElseThrow(
										() -> new IllegalArgumentException("subTask not found"));
						if (!st.getChallenge().getId().equals(ci.getChallenge().getId())) {
							throw new IllegalStateException("subTask does not belong to check-in challenge");
						}
						ci.setSubTask(st);
					} else {
						ci.setSubTask(null);
					}
					ci.setCheckDate(req.checkDate());
					return ResponseEntity.ok(CheckInResponse.from(checkIns.save(ci)));
				})
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/check-ins/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!checkIns.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		checkIns.deleteById(id);
		return ResponseEntity.noContent().build();
	}
}
