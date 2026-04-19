package com.authspring.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.web.dto.ResetPasswordRequest;
import com.challenges.api.model.PasswordResetToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private PasswordResetService passwordResetService;

	@Test
	void reset_returnsUserNotFound_whenEmailUnknown() {
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.empty());

		PasswordResetOutcome outcome = passwordResetService.reset(
				new ResetPasswordRequest("tok", "a@b.c", "newpass12", "newpass12"));

		assertThat(outcome).isInstanceOf(PasswordResetOutcome.UserNotFound.class);
		verify(passwordResetTokenRepository, never()).findById(any());
	}

	@Test
	void reset_returnsInvalidToken_whenRowMissingOrMismatch() {
		User user = new User("N", "a@b.c", "oldhash", "USER");
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
		when(passwordResetTokenRepository.findById("a@b.c")).thenReturn(Optional.empty());

		assertThat(passwordResetService.reset(new ResetPasswordRequest("t", "a@b.c", "newpass12", "newpass12")))
				.isInstanceOf(PasswordResetOutcome.InvalidToken.class);

		when(passwordResetTokenRepository.findById("a@b.c"))
				.thenReturn(Optional.of(new PasswordResetToken("a@b.c", "bcrypt-hash", Instant.now())));
		when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

		assertThat(passwordResetService.reset(new ResetPasswordRequest("wrong", "a@b.c", "newpass12", "newpass12")))
				.isInstanceOf(PasswordResetOutcome.InvalidToken.class);
	}

	@Test
	void reset_success_updatesPasswordDeletesToken() {
		User user = new User("N", "a@b.c", "oldhash", "USER");
		when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));
		when(passwordResetTokenRepository.findById("a@b.c"))
				.thenReturn(Optional.of(new PasswordResetToken("a@b.c", "bcrypt-hash", Instant.now())));
		when(passwordEncoder.matches("plain-tok", "bcrypt-hash")).thenReturn(true);
		when(passwordEncoder.encode("newpass12")).thenReturn("new-hash");

		PasswordResetOutcome outcome = passwordResetService.reset(
				new ResetPasswordRequest("plain-tok", "a@b.c", "newpass12", "newpass12"));

		assertThat(outcome).isInstanceOf(PasswordResetOutcome.Success.class);
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getPassword()).isEqualTo("new-hash");
		assertThat(userCaptor.getValue().getRememberToken()).isNotNull().hasSize(60);
		verify(passwordResetTokenRepository).deleteById("a@b.c");
	}
}
