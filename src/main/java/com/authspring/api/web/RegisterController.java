package com.authspring.api.web;

import com.authspring.api.service.RegisterService;
import com.authspring.api.service.RegistrationOutcome;
import com.authspring.api.web.dto.RegisterRequest;
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
public class RegisterController {

	private final RegisterService registerService;

	public RegisterController(RegisterService registerService) {
		this.registerService = registerService;
	}

	@PostMapping(
			value = { "/register", "/register/" },
			consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResponseEntity<Object> store(@Valid @ModelAttribute RegisterRequest request) {
		return switch (registerService.register(request)) {
			case RegistrationOutcome.Registered(var response) -> ResponseEntity.ok(response);
			case RegistrationOutcome.EmailAlreadyTaken() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
					.body(duplicateEmailProblem());
		};
	}

	private static ProblemDetail duplicateEmailProblem() {
		String msg = "The email has already been taken.";
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), msg);
		pd.setTitle("Registration failed");
		pd.setProperty("message", "The given data was invalid.");
		pd.setProperty("errors", Map.of("email", List.of(msg)));
		return pd;
	}
}
