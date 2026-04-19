package com.authspring.api.web;

import com.authspring.api.service.SessionService;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class LoginController {

	private final SessionService sessionService;

	public LoginController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> loginJson(@Valid @RequestBody LoginRequest request) {
		return loginResult(request);
	}

	@PostMapping(
			value = "/login",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> loginForm(@Valid @ModelAttribute LoginRequest request) {
		return loginResult(request);
	}

	private ResponseEntity<Object> loginResult(LoginRequest request) {
		LoginResponse response = sessionService.login(request);
		if (response == null) {
			return ResponseEntity.status(HttpStatusCode.valueOf(422)).body(invalidLoginProblemDetail());
		}
		return ResponseEntity.ok(response);
	}

	private static ProblemDetail invalidLoginProblemDetail() {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(
				HttpStatusCode.valueOf(422), "The provided credentials are incorrect.");
		pd.setTitle("Invalid credentials");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of("The provided credentials are incorrect.")));
		return pd;
	}
}
