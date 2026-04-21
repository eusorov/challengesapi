package com.challenges.api.config;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.support.JwtLoginSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP-layer auth: allowlisted paths stay reachable without a JWT; other {@code /api/**} routes
 * return {@code 401} when anonymous (see ticket {@code docs/tickets/2026-04-21-security-tighten-api-authentication.md}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityHttpAuthorizationIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;

	@Autowired
	SecurityHttpAuthorizationIT(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void anonymousMayGetPublicChallengeDiscovery() throws Exception {
		mockMvc.perform(get("/api/challenges").header(HV, V1)).andExpect(status().isOk());
		mockMvc.perform(get("/api/categories").header(HV, V1)).andExpect(status().isOk());
	}

	@Test
	void anonymousCannotAccessProtectedApi() throws Exception {
		mockMvc.perform(get("/api/user").header(HV, V1)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/invites").header(HV, V1)).andExpect(status().isUnauthorized());
	}

	@Test
	void postUsersIsAllowlistedForRegistration() throws Exception {
		mockMvc.perform(post("/api/users")
						.header(HV, V1)
						.contentType(APPLICATION_JSON)
						.content("{\"email\":\"sec-allow@y.z\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void authenticatedUserMayAccessProtectedApi() throws Exception {
		mockMvc.perform(post("/api/users")
						.header(HV, V1)
						.contentType(APPLICATION_JSON)
						.content("{\"email\":\"sec-auth@y.z\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		String auth = JwtLoginSupport.bearerAuthorization(mockMvc, "sec-auth@y.z", "password123");
		mockMvc.perform(get("/api/user").header(HV, V1).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk());
	}
}
