package com.authspring.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.config.JwtProperties;
import com.challenges.api.model.PersonalAccessToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PersonalAccessTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalAccessTokenServiceTest {

	@Mock
	private PersonalAccessTokenRepository repository;

	@Mock
	private JwtProperties jwtProperties;

	@InjectMocks
	private PersonalAccessTokenService service;

	@Test
	void recordLoginToken_savesRowWithTokenableInfoAndSha256OfJwt() {
		long expirationMs = 3_600_000L;
		when(jwtProperties.expirationMs()).thenReturn(expirationMs);

		User user = mock(User.class);
		when(user.getId()).thenReturn(7L);

		String jwt = "header.payload.signature";
		service.recordLoginToken(user, jwt);

		ArgumentCaptor<PersonalAccessToken> captor = ArgumentCaptor.forClass(PersonalAccessToken.class);
		verify(repository).save(captor.capture());
		PersonalAccessToken saved = captor.getValue();

		assertThat(saved.getTokenableType()).isEqualTo(User.class.getName());
		assertThat(saved.getTokenableId()).isEqualTo(7L);
		assertThat(saved.getName()).isEqualTo("api");
		assertThat(saved.getToken()).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(saved.getAbilities()).isEqualTo("[\"*\"]");
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusMillis(expirationMs));
	}
}
