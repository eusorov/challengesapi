package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.support.ChallengeConstraints;
import com.challenges.api.support.JwtLoginSupport;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
class SubTaskControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	SubTaskControllerIT(
			MockMvc mockMvc,
			UserRepository users,
			ChallengeRepository challenges,
			ObjectMapper objectMapper,
			PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.challenges = challenges;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
	}

	private Challenge challenge;
	private String bearerAuth;
	private String intruderBearerAuth;

	@BeforeEach
	void setup() throws Exception {
		User u = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "st-owner@test"));
		users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "st-intruder@test"));
		challenge = challenges.save(new Challenge(
				u, "nested", null, LocalDate.of(2026, 2, 1), null, ChallengeCategory.OTHER, null, null, false));
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "st-owner@test", "password");
		intruderBearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "st-intruder@test", "password");
	}

	@Test
	void nestedListAndCrud() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());

		String created =
				mockMvc.perform(
								post("/api/subtasks").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(postBody))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.title").value("Step A"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long subTaskId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/subtasks").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(subTaskId))
				.andExpect(jsonPath("$[0].title").value("Step A"));

		mockMvc.perform(get("/api/subtasks/" + subTaskId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sortIndex").value(0));

		String putBody = "{\"title\":\"Step B\",\"sortIndex\":1}";
		mockMvc.perform(
						put("/api/subtasks/" + subTaskId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(putBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Step B"));

		mockMvc.perform(delete("/api/subtasks/" + subTaskId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth)).andExpect(status().isNoContent());
	}

	@Test
	void create_withoutBearer_returnsUnauthorized() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());
		mockMvc.perform(post("/api/subtasks").header(HV, V1).contentType(APPLICATION_JSON).content(postBody))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void replace_withoutBearer_returnsUnauthorized() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());
		String created =
				mockMvc.perform(
								post("/api/subtasks").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(postBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long subTaskId = objectMapper.readTree(created).get("id").asLong();
		mockMvc.perform(
						put("/api/subtasks/" + subTaskId)
								.header(HV, V1)
								.contentType(APPLICATION_JSON)
								.content("{\"title\":\"X\",\"sortIndex\":0}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void delete_withoutBearer_returnsUnauthorized() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());
		String created =
				mockMvc.perform(
								post("/api/subtasks").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(postBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long subTaskId = objectMapper.readTree(created).get("id").asLong();
		mockMvc.perform(delete("/api/subtasks/" + subTaskId).header(HV, V1)).andExpect(status().isUnauthorized());
	}

	@Test
	void create_asNonOwner_returnsForbidden() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Intruder step\",\"sortIndex\":0}", challenge.getId());
		mockMvc.perform(
						post("/api/subtasks")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, intruderBearerAuth)
								.contentType(APPLICATION_JSON)
								.content(postBody))
				.andExpect(status().isForbidden());
	}

	@Test
	void replace_asNonOwner_returnsForbidden() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());
		String created =
				mockMvc.perform(
								post("/api/subtasks").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(postBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long subTaskId = objectMapper.readTree(created).get("id").asLong();
		mockMvc.perform(
						put("/api/subtasks/" + subTaskId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, intruderBearerAuth)
								.contentType(APPLICATION_JSON)
								.content("{\"title\":\"Hijack\",\"sortIndex\":9}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void delete_asNonOwner_returnsForbidden() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());
		String created =
				mockMvc.perform(
								post("/api/subtasks").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(postBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long subTaskId = objectMapper.readTree(created).get("id").asLong();
		mockMvc.perform(
						delete("/api/subtasks/" + subTaskId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, intruderBearerAuth))
				.andExpect(status().isForbidden());
	}

	@Test
	void createSubtask_returnsBadRequestWhenChallengeAlreadyHasMaxSubtasks() throws Exception {
		for (int i = 0; i < ChallengeConstraints.MAX_SUBTASKS_PER_CHALLENGE; i++) {
			String body = String.format(
					"{\"challengeId\":%d,\"title\":\"st-%d\",\"sortIndex\":%d}",
					challenge.getId(), i, i);
			mockMvc.perform(
							post("/api/subtasks")
									.header(HV, V1)
									.header(HttpHeaders.AUTHORIZATION, bearerAuth)
									.contentType(APPLICATION_JSON)
									.content(body))
					.andExpect(status().isCreated());
		}

		String overflow = String.format(
				"{\"challengeId\":%d,\"title\":\"one-too-many\",\"sortIndex\":99}",
				challenge.getId());
		mockMvc.perform(
						post("/api/subtasks")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(overflow))
				.andExpect(status().isBadRequest());
	}
}
