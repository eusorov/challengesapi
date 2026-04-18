package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ScheduleControllerIT {

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
		User u = users.save(new User("sch-owner@test"));
		challenge = challenges.save(new Challenge(u, "scheduled", null, LocalDate.of(2026, 3, 1), null));
	}

	@Test
	void createDailyChallengeScheduleThenGetAndDelete() throws Exception {
		String body = String.format(
				"{\"challengeId\":%d,\"kind\":\"DAILY\",\"weekDays\":[]}", challenge.getId());

		String created =
				mockMvc.perform(post("/api/schedules").header(HV, V1).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.kind").value("DAILY"))
						.andExpect(jsonPath("$.challengeId").value(challenge.getId().intValue()))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long scheduleId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/schedules/" + scheduleId).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.weekDays").isArray());

		mockMvc.perform(delete("/api/schedules/" + scheduleId).header(HV, V1))
				.andExpect(status().isNoContent());
	}
}
