package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.security.UserPrincipal;
import com.authspring.api.web.dto.AuthUserResponse;
import com.challenges.api.repo.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class CurrentUserController {

	private final UserRepository userRepository;

	public CurrentUserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@RequiresAuth
	@GetMapping("/user")
	public ResponseEntity<AuthUserResponse> currentUser(@AuthenticationPrincipal UserPrincipal principal) {
		return userRepository
				.findById(principal.getId())
				.map(AuthUserResponse::fromEntity)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
}
