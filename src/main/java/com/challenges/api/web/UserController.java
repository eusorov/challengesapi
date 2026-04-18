package com.challenges.api.web;

import com.challenges.api.service.UserService;
import com.challenges.api.web.dto.UserRequest;
import com.challenges.api.web.dto.UserResponse;
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
@RequestMapping(path = "/api/users", version = "1")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public @NonNull List<UserResponse> list() {
		return userService.listUsers().stream().map(UserResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> get(@PathVariable Long id) {
		return userService.findById(id)
				.map(UserResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(userService.create(req.email())));
	}

	@PutMapping("/{id}")
	public ResponseEntity<UserResponse> replace(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
		return userService.replace(id, req.email())
				.map(UserResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!userService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
