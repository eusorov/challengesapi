package com.authspring.api.service;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.AuthUserResponse;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final PersonalAccessTokenService personalAccessTokenService;

	public LoginService(
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
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email()).orElse(null);
		if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			return null;
		}
		String token = jwtService.createToken(user);
		personalAccessTokenService.recordLoginToken(user, token);
		return new LoginResponse(token, AuthUserResponse.fromEntity(user));
	}
}
