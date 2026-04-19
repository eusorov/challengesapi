package com.authspring.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.security.SignedUrlSigner;
import com.authspring.api.security.UserPrincipal;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationNotificationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private SignedUrlSigner signedUrlSigner;

	@Mock
	private EmailVerificationMailSender mailSender;

	@InjectMocks
	private EmailVerificationNotificationService service;

	@Test
	void send_returnsAlreadyVerified_whenEmailVerifiedAtSet() throws Exception {
		User u = mock(User.class);
		when(u.getId()).thenReturn(1L);
		when(u.getEmail()).thenReturn("ada@example.com");
		when(u.getRole()).thenReturn("user");
		when(u.getEmailVerifiedAt()).thenReturn(Instant.parse("2020-01-01T00:00:00Z"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(u));

		assertThat(service.send(new UserPrincipal(u)))
				.isInstanceOf(EmailVerificationNotificationOutcome.AlreadyVerified.class);
		verify(mailSender, never()).send(any(), any());
		verify(signedUrlSigner, never()).buildVerifyEmailUrl(anyLong(), any());
	}

	@Test
	void send_sendsMailAndReturnsSent_whenUnverified() throws Exception {
		User u = mock(User.class);
		when(u.getId()).thenReturn(2L);
		when(u.getEmail()).thenReturn("b@example.com");
		when(u.getRole()).thenReturn("user");
		when(u.getEmailVerifiedAt()).thenReturn(null);
		when(userRepository.findById(2L)).thenReturn(Optional.of(u));
		when(signedUrlSigner.buildVerifyEmailUrl(2L, "b@example.com")).thenReturn("https://app/verify");

		assertThat(service.send(new UserPrincipal(u)))
				.isInstanceOf(EmailVerificationNotificationOutcome.Sent.class);
		verify(mailSender).send(u, "https://app/verify");
	}

	@Test
	void send_throws_whenUserMissing() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());
		User u = mock(User.class);
		when(u.getId()).thenReturn(99L);
		when(u.getEmail()).thenReturn("x@y.z");
		when(u.getRole()).thenReturn("user");
		when(u.getEmailVerifiedAt()).thenReturn(null);

		assertThatThrownBy(() -> service.send(new UserPrincipal(u)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("99");
	}
}
