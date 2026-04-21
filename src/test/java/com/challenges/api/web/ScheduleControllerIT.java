package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
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
class ScheduleControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	ScheduleControllerIT(
			MockMvc mockMvc,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ObjectMapper objectMapper,
			PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
	}

	private Challenge challenge;
	private String bearerAuth;

	@BeforeEach
	void setup() throws Exception {
		User u = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "sch-owner@test"));
		challenge = challenges.save(new Challenge(
				u, "scheduled", null, LocalDate.of(2026, 3, 1), null, ChallengeCategory.OTHER));
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "sch-owner@test", "password");
	}

	@Test
	void createDailyChallengeScheduleThenGetAndDelete() throws Exception {
		String body = String.format(
				"{\"challengeId\":%d,\"kind\":\"DAILY\",\"weekDays\":[]}", challenge.getId());

		String created =
				mockMvc.perform(post("/api/schedules").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.kind").value("DAILY"))
						.andExpect(jsonPath("$.challengeId").value(challenge.getId().intValue()))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long scheduleId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/schedules/" + scheduleId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.weekDays").isArray());

		mockMvc.perform(delete("/api/schedules/" + scheduleId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNoContent());
	}

	@Test
	void createDailySubTaskScheduleThenGetAndDelete() throws Exception {
		SubTask st = subTasks.save(new SubTask(challenge, "sub step", 0));
		String body = String.format("{\"subTaskId\":%d,\"kind\":\"DAILY\",\"weekDays\":[]}", st.getId());

		String created =
				mockMvc.perform(post("/api/schedules").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.kind").value("DAILY"))
						.andExpect(jsonPath("$.subTaskId").value(st.getId().intValue()))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long scheduleId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/schedules/" + scheduleId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challengeId").doesNotExist())
				.andExpect(jsonPath("$.subTaskId").value(st.getId().intValue()));

		mockMvc.perform(delete("/api/schedules/" + scheduleId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNoContent());
	}
}
