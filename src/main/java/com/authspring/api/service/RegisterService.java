package com.authspring.api.service;

import static com.challenges.api.model.User.DEFAULT_ROLE;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.AuthUserResponse;
import com.authspring.api.web.dto.RegisterRequest;
import com.authspring.api.web.dto.RegisterResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterService {

	public static final String SUCCESS_MESSAGE =
			"User registered successfully. Please check your email to verify your account.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final PersonalAccessTokenService personalAccessTokenService;

	public RegisterService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			PersonalAccessTokenService personalAccessTokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.personalAccessTokenService = personalAccessTokenService;
	}

	@Transactional
	public RegistrationOutcome register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.findByEmail(email).isPresent()) {
			return new RegistrationOutcome.EmailAlreadyTaken();
		}
		User user = new User(
				request.name().trim(),
				email,
				passwordEncoder.encode(request.password()),
				DEFAULT_ROLE);
		userRepository.save(user);
		String token = jwtService.createToken(user);
		personalAccessTokenService.recordLoginToken(user, token);
		RegisterResponse response =
				new RegisterResponse(SUCCESS_MESSAGE, token, AuthUserResponse.fromEntity(user));
		return new RegistrationOutcome.Registered(response);
	}
}
