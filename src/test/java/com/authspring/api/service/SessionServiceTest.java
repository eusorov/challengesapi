package com.authspring.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private PersonalAccessTokenService personalAccessTokenService;

	@InjectMocks
	private SessionService sessionService;

	@Test
	void login_returnsNull_whenUserMissing() {
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.empty());

		assertThat(sessionService.login(new LoginRequest("a@b.c", "x"))).isNull();

		verifyNoInteractions(passwordEncoder, jwtService, personalAccessTokenService);
	}

	@Test
	void login_returnsNull_whenPasswordMismatch() {
		User user = mock(User.class);
		when(user.getPassword()).thenReturn("hash");
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThat(sessionService.login(new LoginRequest("a@b.c", "wrong"))).isNull();

		verifyNoInteractions(jwtService, personalAccessTokenService);
	}

	@Test
	void login_returnsTokenAndRecordsPat_whenCredentialsOk() {
		Instant t = Instant.parse("2026-01-01T00:00:00Z");
		User user = mock(User.class);
		when(user.getId()).thenReturn(5L);
		when(user.getEmail()).thenReturn("a@b.c");
		when(user.getName()).thenReturn("N");
		when(user.getRole()).thenReturn("user");
		when(user.getCreatedAt()).thenReturn(t);
		when(user.getUpdatedAt()).thenReturn(t);
		when(user.getPassword()).thenReturn("hash");
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
		when(jwtService.createToken(user)).thenReturn("jwt-compact");

		LoginResponse res = sessionService.login(new LoginRequest("a@b.c", "secret"));

		assertThat(res.token()).isEqualTo("jwt-compact");
		assertThat(res.user().id()).isEqualTo(5L);
		assertThat(res.user().email()).isEqualTo("a@b.c");
		assertThat(res.user().name()).isEqualTo("N");
		verify(personalAccessTokenService).recordLoginToken(user, "jwt-compact");
	}
}
