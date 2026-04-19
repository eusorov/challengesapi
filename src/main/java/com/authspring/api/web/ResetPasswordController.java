package com.authspring.api.web;

import com.authspring.api.service.PasswordResetOutcome;
import com.authspring.api.service.PasswordResetService;
import com.authspring.api.web.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class ResetPasswordController {

	private final PasswordResetService passwordResetService;

	public ResetPasswordController(PasswordResetService passwordResetService) {
		this.passwordResetService = passwordResetService;
	}

	@PostMapping(
			value = "/reset-password",
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> store(@Valid @ModelAttribute ResetPasswordRequest request) {
		return switch (passwordResetService.reset(request)) {
			case PasswordResetOutcome.Success() -> ResponseEntity.ok(Map.of("status", "Your password has been reset."));
			case PasswordResetOutcome.UserNotFound() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(resetFailedProblem("We can't find a user with that email address."));
			case PasswordResetOutcome.InvalidToken() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(resetFailedProblem("This password reset token is invalid."));
		};
	}

	private static ProblemDetail resetFailedProblem(String emailMessage) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), emailMessage);
		pd.setTitle("Password reset failed");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of(emailMessage)));
		return pd;
	}
}
