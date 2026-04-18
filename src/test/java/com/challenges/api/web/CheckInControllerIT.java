package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
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
class CheckInControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private ChallengeRepository challenges;

	@Autowired
	private SubTaskRepository subTasks;

	@Autowired
	private ObjectMapper objectMapper;

	private User user;
	private Challenge challenge;
	private SubTask otherSubTask;

	@BeforeEach
	void setup() {
		user = users.save(new User("ci-user@test"));
		User other = users.save(new User("ci-other@test"));
		challenge = challenges.save(new Challenge(user, "ci-ch", null, LocalDate.of(2026, 5, 1), null));
		Challenge otherCh = challenges.save(new Challenge(other, "other", null, LocalDate.of(2026, 5, 2), null));
		otherSubTask = subTasks.save(new SubTask(otherCh, "foreign", 0));
	}

	@Test
	void createListAndRejectCrossChallengeSubTask() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-10\",\"subTaskId\":null}",
				user.getId(), challenge.getId());

		String created =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).contentType(APPLICATION_JSON).content(createBody))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.checkDate").value("2026-05-10"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long checkInId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/check-ins").header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(checkInId));

		String badPut = String.format(
				"{\"checkDate\":\"2026-05-11\",\"subTaskId\":%d}", otherSubTask.getId());
		mockMvc.perform(
						put("/api/check-ins/" + checkInId).header(HV, V1).contentType(APPLICATION_JSON).content(badPut))
				.andExpect(status().isBadRequest());

		String okPut = "{\"checkDate\":\"2026-05-12\",\"subTaskId\":null}";
		mockMvc.perform(
						put("/api/check-ins/" + checkInId).header(HV, V1).contentType(APPLICATION_JSON).content(okPut))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkDate").value("2026-05-12"));
	}
}
