package com.authspring.api.service;

import static com.authspring.api.service.RegisterService.SUCCESS_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.RegisterRequest;
import com.authspring.api.web.dto.RegisterResponse;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private PersonalAccessTokenService personalAccessTokenService;

	@InjectMocks
	private RegisterService registerService;

	@Test
	void register_returnsEmailAlreadyTaken_whenEmailExists() {
		when(userRepository.findByEmail("ada@example.com"))
				.thenReturn(Optional.of(new User("X", "ada@example.com", "h", User.DEFAULT_ROLE)));

		RegistrationOutcome outcome =
				registerService.register(new RegisterRequest("Ada", "ada@example.com", "password12", "password12"));

		assertThat(outcome).isInstanceOf(RegistrationOutcome.EmailAlreadyTaken.class);
		verify(userRepository, never()).save(any());
		verify(jwtService, never()).createToken(any());
	}

	@Test
	void register_savesUserIssuesJwtAndRecordsPat_whenEmailFree() throws Exception {
		when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password12")).thenReturn("ENC");
		when(userRepository.save(any(User.class)))
				.thenAnswer(invocation -> {
					User u = invocation.getArgument(0);
					assignUserId(u, 99L);
					return u;
				});
		when(jwtService.createToken(any(User.class))).thenReturn("jwt");

		RegistrationOutcome outcome =
				registerService.register(new RegisterRequest("Ada", "Ada@Example.com", "password12", "password12"));

		assertThat(outcome).isInstanceOf(RegistrationOutcome.Registered.class);
		RegisterResponse res = ((RegistrationOutcome.Registered) outcome).response();
		assertThat(res.message()).isEqualTo(SUCCESS_MESSAGE);
		assertThat(res.token()).isEqualTo("jwt");
		assertThat(res.user().email()).isEqualTo("ada@example.com");
		assertThat(res.user().name()).isEqualTo("Ada");
		assertThat(res.user().id()).isEqualTo(99L);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User saved = userCaptor.getValue();
		assertThat(saved.getEmail()).isEqualTo("ada@example.com");
		assertThat(saved.getPassword()).isEqualTo("ENC");

		verify(personalAccessTokenService).recordLoginToken(any(User.class), eq("jwt"));
	}

	private static void assignUserId(User user, long id) throws Exception {
		Field f = User.class.getDeclaredField("id");
		f.setAccessible(true);
		f.set(user, id);
	}
}
