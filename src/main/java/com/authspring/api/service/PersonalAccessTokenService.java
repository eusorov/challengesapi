package com.authspring.api.service;

import com.authspring.api.config.JwtProperties;
import com.challenges.api.model.PersonalAccessToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PersonalAccessTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccessTokenService {

	private static final String BEARER_PREFIX = "Bearer ";

	private final PersonalAccessTokenRepository repository;
	private final JwtProperties jwtProperties;

	public PersonalAccessTokenService(
			PersonalAccessTokenRepository repository, JwtProperties jwtProperties) {
		this.repository = repository;
		this.jwtProperties = jwtProperties;
	}

	@Transactional
	public void recordLoginToken(User user, String jwtCompact) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusMillis(jwtProperties.expirationMs());
		PersonalAccessToken row = new PersonalAccessToken();
		row.setTokenableType(User.class.getName());
		row.setTokenableId(user.getId());
		row.setName("api");
		row.setToken(sha256Hex(jwtCompact));
		row.setAbilities("[\"*\"]");
		row.setExpiresAt(expiresAt);
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		repository.save(row);
	}

	@Transactional(readOnly = true)
	public boolean existsForJwtCompact(String jwtCompact) {
		return repository.findByToken(sha256Hex(jwtCompact)).isPresent();
	}

	@Transactional
	public void revokeByJwtFromRequest(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return;
		}
		String raw = header.substring(BEARER_PREFIX.length()).trim();
		if (raw.isEmpty()) {
			return;
		}
		repository.deleteByToken(sha256Hex(raw));
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
