package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.closeTo;
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
						+ "\"startDate\":\"2026-01-01\",\"endDate\":null,\"category\":\"PRODUCTIVITY\"}",
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
						.andExpect(jsonPath("$.category").value("PRODUCTIVITY"))
						.andExpect(jsonPath("$.private").value(false))
						.andExpect(jsonPath("$.subtasks").isArray())
						.andReturn()
						.getResponse()
						.getContentAsString();

		JsonNode node = objectMapper.readTree(created);
		long challengeId = node.get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challengeId + "/participants")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId").value(owner1.getId().intValue()))
				.andExpect(jsonPath("$.content[0].subTaskId").value((Object) null));

		mockMvc.perform(get("/api/challenges").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].ownerUserId").value(owner1.getId().intValue()))
				.andExpect(jsonPath("$.content[0].subtasks").isArray());

		mockMvc.perform(get("/api/challenges/" + challengeId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerUserId").value(owner1.getId().intValue()))
				.andExpect(jsonPath("$.subtasks").isArray());
	}

	@Test
	void createWithLocation_roundTripsCityAndCoordinates() throws Exception {
		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Berlin run\",\"description\":null,"
						+ "\"startDate\":\"2026-02-01\",\"endDate\":null,\"category\":\"HEALTH_AND_FITNESS\","
						+ "\"city\":\"Berlin\",\"location\":{\"latitude\":52.52,\"longitude\":13.405}}",
				owner1.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.city").value("Berlin"))
						.andExpect(jsonPath("$.location.latitude", closeTo(52.52, 1e-6)))
						.andExpect(jsonPath("$.location.longitude", closeTo(13.405, 1e-6)))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challengeId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.city").value("Berlin"))
				.andExpect(jsonPath("$.location.latitude", closeTo(52.52, 1e-6)))
				.andExpect(jsonPath("$.location.longitude", closeTo(13.405, 1e-6)));
	}

	@Test
	void createWithCityOnly_returnsBadRequest() throws Exception {
		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"No coords\",\"description\":null,"
						+ "\"startDate\":\"2026-03-01\",\"endDate\":null,\"category\":\"OTHER\","
						+ "\"city\":\"Paris\"}",
				owner1.getId());

		mockMvc.perform(post("/api/challenges")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listChallenges_omitsPrivate_butGetByIdStillWorks() throws Exception {
		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Secret ch\",\"description\":null,"
						+ "\"startDate\":\"2026-06-01\",\"endDate\":null,\"category\":\"LEARNING\",\"private\":true}",
				owner1.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.private").value(true))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.numberOfElements").value(0));

		mockMvc.perform(get("/api/challenges/" + challengeId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Secret ch"))
				.andExpect(jsonPath("$.private").value(true));
	}
}
