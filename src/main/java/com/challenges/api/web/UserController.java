package com.challenges.api.web;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.web.dto.UserRequest;
import com.challenges.api.web.dto.UserResponse;
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
@RequestMapping(path = "/api/users", version = "1")
public class UserController {

	private final UserRepository users;

	public UserController(UserRepository users) {
		this.users = users;
	}

	@GetMapping
	public List<UserResponse> list() {
		return users.findAll().stream().map(UserResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> get(@PathVariable Long id) {
		return users.findById(id)
				.map(UserResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest req) {
		User u = users.save(new User(req.email()));
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(u));
	}

	@PutMapping("/{id}")
	public ResponseEntity<UserResponse> replace(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
		return users.findById(id)
				.map(u -> {
					u.setEmail(req.email());
					return UserResponse.from(users.save(u));
				})
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!users.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		users.deleteById(id);
		return ResponseEntity.noContent().build();
	}
}
