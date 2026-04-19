package com.authspring.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.authspring.api.config.JwtProperties;
import com.challenges.api.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private static final String SECRET_32 = "a".repeat(32);

	@Test
	void constructor_rejectsSecretShorterThan32Bytes() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties("short", 1000L, 1000L)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("jwt.secret");
	}

	@Test
	void createToken_setsSubjectEmailAndExpiration() {
		long expirationMs = 60_000L;
		JwtService svc = new JwtService(new JwtProperties(SECRET_32, expirationMs, 3600_000L));
		User user = mock(User.class);
		when(user.getId()).thenReturn(99L);
		when(user.getEmail()).thenReturn("u@example.com");

		String jwt = svc.createToken(user);

		SecretKey key = Keys.hmacShaKeyFor(SECRET_32.getBytes(StandardCharsets.UTF_8));
		Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

		assertThat(claims.getSubject()).isEqualTo("99");
		assertThat(claims.get("email", String.class)).isEqualTo("u@example.com");
		assertThat(claims.getExpiration().toInstant()).isAfter(Instant.now());
		assertThat(claims.getExpiration().toInstant())
				.isBeforeOrEqualTo(Instant.now().plusMillis(expirationMs + 5_000L));
	}
}
