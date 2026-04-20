package com.challenges.api.web;

import com.challenges.api.service.SubTaskService;
import com.challenges.api.web.dto.SubTaskRequest;
import com.challenges.api.web.dto.SubTaskResponse;
import com.challenges.api.web.dto.SubTaskUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.NonNull;
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

	private final SubTaskService subTaskService;

	public SubTaskController(SubTaskService subTaskService) {
		this.subTaskService = subTaskService;
	}

	@GetMapping({
		"/challenges/{challengeId:\\d+}/subtasks",
		"/challenges/{challengeId:\\d+}/subtasks/"
	})
	public @NonNull List<SubTaskResponse> listForChallenge(@PathVariable Long challengeId) {
		return subTaskService.listForChallenge(challengeId).stream().map(SubTaskResponse::from).toList();
	}

	@GetMapping({ "/subtasks/{id}", "/subtasks/{id}/" })
	public ResponseEntity<SubTaskResponse> get(@PathVariable Long id) {
		return subTaskService.findById(id)
				.map(SubTaskResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping({ "/subtasks", "/subtasks/" })
	public ResponseEntity<SubTaskResponse> create(@Valid @RequestBody SubTaskRequest req) {
		return subTaskService.create(req)
				.map(st -> ResponseEntity.status(HttpStatus.CREATED).body(SubTaskResponse.from(st)))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping({ "/subtasks/{id}", "/subtasks/{id}/" })
	public ResponseEntity<SubTaskResponse> replace(
			@PathVariable Long id, @Valid @RequestBody SubTaskUpdateRequest req) {
		return subTaskService.replace(id, req)
				.map(SubTaskResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping({ "/subtasks/{id}", "/subtasks/{id}/" })
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!subTaskService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
