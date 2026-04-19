package com.authspring.api.service;

import com.authspring.api.config.JwtProperties;
import com.challenges.api.model.PersonalAccessToken;
import com.challenges.api.model.User;
import com.challenges.api.repo.PersonalAccessTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccessTokenService {

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
