package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.service.PersonalAccessTokenService;
import com.authspring.api.service.LoginService;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
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

	private final LoginService loginService;
	private final PersonalAccessTokenService personalAccessTokenService;

	public LoginController(LoginService loginService, PersonalAccessTokenService personalAccessTokenService) {
		this.loginService = loginService;
		this.personalAccessTokenService = personalAccessTokenService;
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
		LoginResponse response = loginService.login(request);
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

	@RequiresAuth
	@PostMapping("/logout")
	public Map<String, String> destroy(HttpServletRequest request) {
		personalAccessTokenService.revokeByJwtFromRequest(request);
		return Map.of("message", "Logged out");
	}
}
