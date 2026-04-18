package com.challenges.api.web;

import com.challenges.api.service.CommentService;
import com.challenges.api.web.dto.CommentRequest;
import com.challenges.api.web.dto.CommentResponse;
import com.challenges.api.web.dto.CommentUpdateRequest;
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
@RequestMapping(path = "/api", version = "1")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@GetMapping("/challenges/{challengeId}/comments")
	public List<CommentResponse> list(
			@PathVariable Long challengeId, @RequestParam(required = false) Long subTaskId) {
		return commentService.listForChallenge(challengeId, subTaskId).stream()
				.map(CommentResponse::from)
				.toList();
	}

	@PostMapping("/challenges/{challengeId}/comments")
	public ResponseEntity<CommentResponse> create(
			@PathVariable Long challengeId, @Valid @RequestBody CommentRequest req) {
		return commentService.create(challengeId, req)
				.map(c -> ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(c)))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/comments/{id}")
	public ResponseEntity<CommentResponse> get(@PathVariable Long id) {
		return commentService.findById(id)
				.map(CommentResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/comments/{id}")
	public ResponseEntity<CommentResponse> replace(
			@PathVariable Long id, @Valid @RequestBody CommentUpdateRequest req) {
		return commentService.update(id, req)
				.map(CommentResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/comments/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!commentService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
