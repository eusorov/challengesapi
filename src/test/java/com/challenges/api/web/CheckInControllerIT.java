package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.Participant;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
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
class CheckInControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ParticipantRepository participants;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	CheckInControllerIT(
			MockMvc mockMvc,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ParticipantRepository participants,
			ObjectMapper objectMapper,
			PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.participants = participants;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
	}

	private User user;
	private User otherUser;
	private Challenge challenge;
	private SubTask otherSubTask;
	private String bearerAuth;
	private String otherBearerAuth;

	@BeforeEach
	void setup() throws Exception {
		user = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "ci-user@test"));
		otherUser = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "ci-other@test"));
		challenge = challenges.save(new Challenge(
				user, "ci-ch", null, LocalDate.of(2026, 5, 1), null, ChallengeCategory.OTHER));
		Challenge otherCh = challenges.save(new Challenge(
				otherUser, "other", null, LocalDate.of(2026, 5, 2), null, ChallengeCategory.OTHER));
		otherSubTask = subTasks.save(new SubTask(otherCh, "foreign", 0));
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "ci-user@test", "password");
		otherBearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "ci-other@test", "password");
	}

	@Test
	void createListAndRejectCrossChallengeSubTask() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-10\",\"subTaskId\":null}",
				user.getId(), challenge.getId());

		String created =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(createBody))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.checkDate").value("2026-05-10"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long checkInId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(checkInId));

		String badPut = String.format(
				"{\"checkDate\":\"2026-05-11\",\"subTaskId\":%d}", otherSubTask.getId());
		mockMvc.perform(
						put("/api/check-ins/" + checkInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(badPut))
				.andExpect(status().isBadRequest());

		String okPut = "{\"checkDate\":\"2026-05-12\",\"subTaskId\":null}";
		mockMvc.perform(
						put("/api/check-ins/" + checkInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(okPut))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkDate").value("2026-05-12"));
	}

	@Test
	void readCheckInsRequiresAuthAndMembership() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-20\",\"subTaskId\":null}",
				user.getId(), challenge.getId());
		String created =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long checkInId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/check-ins").header(HV, V1))
				.andExpect(status().isNotFound());

		mockMvc.perform(
						get("/api/challenges/" + challenge.getId() + "/check-ins")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, otherBearerAuth))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/check-ins/" + checkInId).header(HV, V1)).andExpect(status().isNotFound());

		mockMvc.perform(get("/api/check-ins/" + checkInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, otherBearerAuth))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(checkInId));

		mockMvc.perform(get("/api/check-ins/" + checkInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(checkInId));
	}

	@Test
	void createCheckInRequiresAuthentication() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-21\",\"subTaskId\":null}",
				user.getId(), challenge.getId());
		mockMvc.perform(post("/api/check-ins").header(HV, V1).contentType(APPLICATION_JSON).content(createBody))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createCheckInForbiddenWhenUserIdDoesNotMatchPrincipal() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-22\",\"subTaskId\":null}",
				user.getId(), challenge.getId());
		mockMvc.perform(post("/api/check-ins")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, otherBearerAuth)
						.contentType(APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isForbidden());
	}

	@Test
	void createCheckInForbiddenWhenNotParticipant() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-23\",\"subTaskId\":null}",
				otherUser.getId(), challenge.getId());
		mockMvc.perform(post("/api/check-ins")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, otherBearerAuth)
						.contentType(APPLICATION_JSON)
						.content(createBody))
				.andExpect(status().isForbidden());
	}

	@Test
	void subtaskScopedParticipantMayOnlyCheckInForThatSubtask() throws Exception {
		SubTask st = subTasks.save(new SubTask(challenge, "scoped", 0));
		participants.save(new Participant(otherUser, challenge, st));

		String challengeWide = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-24\",\"subTaskId\":null}",
				otherUser.getId(), challenge.getId());
		mockMvc.perform(post("/api/check-ins")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, otherBearerAuth)
						.contentType(APPLICATION_JSON)
						.content(challengeWide))
				.andExpect(status().isForbidden());

		String scoped = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-24\",\"subTaskId\":%d}",
				otherUser.getId(), challenge.getId(), st.getId());
		mockMvc.perform(post("/api/check-ins")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, otherBearerAuth)
						.contentType(APPLICATION_JSON)
						.content(scoped))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subTaskId").value(st.getId().intValue()));
	}
}
