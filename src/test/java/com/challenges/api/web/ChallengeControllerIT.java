package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.support.JwtLoginSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	ChallengeControllerIT(
			MockMvc mockMvc, UserRepository users, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
	}

	private User owner1;
	private String bearerAuth;

	@BeforeEach
	void setup() throws Exception {
		owner1 = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "ch-owner1@test"));
		users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "ch-owner2@test"));
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "ch-owner1@test", "password");
	}

	@Test
	void createChallengeThenGetById() throws Exception {
		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"My ch\",\"description\":null,"
						+ "\"startDate\":\"2026-01-01\",\"endDate\":null}",
				owner1.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.ownerUserId").value(owner1.getId().intValue()))
						.andExpect(jsonPath("$.title").value("My ch"))
						.andExpect(jsonPath("$.subtasks").isArray())
						.andReturn()
						.getResponse()
						.getContentAsString();

		JsonNode node = objectMapper.readTree(created);
		long challengeId = node.get("id").asLong();

		mockMvc.perform(get("/api/challenges").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].ownerUserId").value(owner1.getId().intValue()))
				.andExpect(jsonPath("$[0].subtasks").isArray());

		mockMvc.perform(get("/api/challenges/" + challengeId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerUserId").value(owner1.getId().intValue()))
				.andExpect(jsonPath("$.subtasks").isArray());
	}
}
