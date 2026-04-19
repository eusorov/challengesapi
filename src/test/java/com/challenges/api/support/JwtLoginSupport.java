package com.challenges.api.support;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.User;
import com.jayway.jsonpath.JsonPath;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Obtains a Bearer access token via {@code POST /api/login} for integration tests. */
public final class JwtLoginSupport {

	private static final String API_VERSION = "API-Version";

	private JwtLoginSupport() {}

	/**
	 * User with raw login password {@code "password"} (encoded with the app {@link PasswordEncoder}) for tests
	 * that call {@link #bearerAuthorization}.
	 */
	public static User userWithLoginPassword(PasswordEncoder passwordEncoder, String email) {
		return new User("User", email, passwordEncoder.encode("password"), User.DEFAULT_ROLE);
	}

	/** Returns a value suitable for {@code Authorization} (includes {@code Bearer } prefix). */
	public static String bearerAuthorization(MockMvc mockMvc, String email, String password) throws Exception {
		MvcResult r = mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String token = JsonPath.read(r.getResponse().getContentAsString(), "$.token");
		return "Bearer " + token;
	}
}
