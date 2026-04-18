package com.challenges.api.web;

import com.challenges.api.model.SubTask;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.web.dto.SubTaskRequest;
import com.challenges.api.web.dto.SubTaskResponse;
import com.challenges.api.web.dto.SubTaskUpdateRequest;
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
public class SubTaskController {

	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;

	public SubTaskController(ChallengeRepository challenges, SubTaskRepository subTasks) {
		this.challenges = challenges;
		this.subTasks = subTasks;
	}

	@GetMapping("/challenges/{challengeId}/subtasks")
	public List<SubTaskResponse> listForChallenge(@PathVariable Long challengeId) {
		return subTasks.findByChallenge_IdOrderBySortIndexAsc(challengeId).stream()
				.map(SubTaskResponse::from)
				.toList();
	}

	@GetMapping("/subtasks/{id}")
	public ResponseEntity<SubTaskResponse> get(@PathVariable Long id) {
		return subTasks.findById(id)
				.map(SubTaskResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/subtasks")
	public ResponseEntity<SubTaskResponse> create(@Valid @RequestBody SubTaskRequest req) {
		return challenges.findById(req.challengeId())
				.map(ch -> {
					SubTask st = subTasks.save(new SubTask(ch, req.title(), req.sortIndex()));
					return ResponseEntity.status(HttpStatus.CREATED).body(SubTaskResponse.from(st));
				})
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/subtasks/{id}")
	public ResponseEntity<SubTaskResponse> replace(
			@PathVariable Long id, @Valid @RequestBody SubTaskUpdateRequest req) {
		return subTasks.findById(id)
				.map(st -> {
					st.setTitle(req.title());
					st.setSortIndex(req.sortIndex());
					return ResponseEntity.ok(SubTaskResponse.from(subTasks.save(st)));
				})
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/subtasks/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!subTasks.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		subTasks.deleteById(id);
		return ResponseEntity.noContent().build();
	}
}
