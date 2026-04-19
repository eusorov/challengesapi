package com.authspring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class EmailVerificationHashes {

	private static final HexFormat HEX = HexFormat.of();

	private EmailVerificationHashes() {}

	public static String sha256Hex(String email) {
		Objects.requireNonNull(email, "email");
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(email.getBytes(StandardCharsets.UTF_8));
			return HEX.formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 MessageDigest not available", e);
		}
	}
}
