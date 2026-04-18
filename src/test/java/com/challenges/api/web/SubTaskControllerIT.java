package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubTaskControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private ChallengeRepository challenges;

	@Autowired
	private ObjectMapper objectMapper;

	private Challenge challenge;

	@BeforeEach
	void setup() {
		User u = users.save(new User("st-owner@test"));
		challenge = challenges.save(new Challenge(u, "nested", null, LocalDate.of(2026, 2, 1), null));
	}

	@Test
	void nestedListAndCrud() throws Exception {
		String postBody = String.format(
				"{\"challengeId\":%d,\"title\":\"Step A\",\"sortIndex\":0}", challenge.getId());

		String created =
				mockMvc.perform(
								post("/api/subtasks").header(HV, V1).contentType(APPLICATION_JSON).content(postBody))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.title").value("Step A"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long subTaskId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/subtasks").header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(subTaskId))
				.andExpect(jsonPath("$[0].title").value("Step A"));

		mockMvc.perform(get("/api/subtasks/" + subTaskId).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sortIndex").value(0));

		String putBody = "{\"title\":\"Step B\",\"sortIndex\":1}";
		mockMvc.perform(
						put("/api/subtasks/" + subTaskId).header(HV, V1).contentType(APPLICATION_JSON).content(putBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Step B"));

		mockMvc.perform(delete("/api/subtasks/" + subTaskId).header(HV, V1)).andExpect(status().isNoContent());
	}
}
