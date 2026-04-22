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
				.andExpect(status().isUnauthorized());

		mockMvc.perform(
						get("/api/challenges/" + challenge.getId() + "/check-ins")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, otherBearerAuth))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/check-ins/" + checkInId).header(HV, V1)).andExpect(status().isUnauthorized());

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

	@Test
	void replaceAndDeleteCheckInRequireAuthentication() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-06-01\",\"subTaskId\":null}",
				user.getId(), challenge.getId());
		String created =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long checkInId = objectMapper.readTree(created).get("id").asLong();
		String putBody = "{\"checkDate\":\"2026-06-02\",\"subTaskId\":null}";

		mockMvc.perform(put("/api/check-ins/" + checkInId).header(HV, V1).contentType(APPLICATION_JSON).content(putBody))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/check-ins/" + checkInId).header(HV, V1)).andExpect(status().isUnauthorized());
	}

	@Test
	void onlyCheckInAuthorMayReplaceOrDelete() throws Exception {
		participants.save(new Participant(otherUser, challenge));

		String authorBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-06-10\",\"subTaskId\":null}",
				user.getId(), challenge.getId());
		String authorCreated =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(authorBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long authorCheckInId = objectMapper.readTree(authorCreated).get("id").asLong();

		String otherBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-06-11\",\"subTaskId\":null}",
				otherUser.getId(), challenge.getId());
		String otherCreated =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, otherBearerAuth).contentType(APPLICATION_JSON).content(otherBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long otherCheckInId = objectMapper.readTree(otherCreated).get("id").asLong();

		String putBody = "{\"checkDate\":\"2026-06-20\",\"subTaskId\":null}";
		mockMvc.perform(
						put("/api/check-ins/" + authorCheckInId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, otherBearerAuth)
								.contentType(APPLICATION_JSON)
								.content(putBody))
				.andExpect(status().isForbidden());
		mockMvc.perform(
						delete("/api/check-ins/" + authorCheckInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, otherBearerAuth))
				.andExpect(status().isForbidden());

		mockMvc.perform(
						put("/api/check-ins/" + otherCheckInId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(putBody))
				.andExpect(status().isForbidden());
		mockMvc.perform(
						delete("/api/check-ins/" + otherCheckInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isForbidden());

		mockMvc.perform(
						put("/api/check-ins/" + authorCheckInId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(putBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkDate").value("2026-06-20"));

		mockMvc.perform(
						delete("/api/check-ins/" + otherCheckInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, otherBearerAuth))
				.andExpect(status().isNoContent());
	}

	@Test
	void replaceOrDeleteUnknownCheckInReturnsNotFound() throws Exception {
		mockMvc.perform(
						put("/api/check-ins/999999999")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content("{\"checkDate\":\"2026-06-30\",\"subTaskId\":null}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/check-ins/999999999").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNotFound());
	}

	@Test
	void replaceOrDeleteWhenNotAllowedToReadChallengeReturnsNotFound() throws Exception {
		String createBody = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-07-01\",\"subTaskId\":null}",
				user.getId(), challenge.getId());
		String created =
				mockMvc.perform(post("/api/check-ins").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long checkInId = objectMapper.readTree(created).get("id").asLong();
		String putBody = "{\"checkDate\":\"2026-07-02\",\"subTaskId\":null}";

		mockMvc.perform(
						put("/api/check-ins/" + checkInId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, otherBearerAuth)
								.contentType(APPLICATION_JSON)
								.content(putBody))
				.andExpect(status().isNotFound());
		mockMvc.perform(
						delete("/api/check-ins/" + checkInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, otherBearerAuth))
				.andExpect(status().isNotFound());
	}

	@Test
	void pendingInviteeMayReadPrivateChallengeButNotCheckIns() throws Exception {
		User owner = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "ci-pend-owner@test"));
		users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "ci-pend-inv@test"));
		String ownerBearer = JwtLoginSupport.bearerAuthorization(mockMvc, "ci-pend-owner@test", "password");
		String inviteeBearer = JwtLoginSupport.bearerAuthorization(mockMvc, "ci-pend-inv@test", "password");

		String createChallenge = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Private ci\",\"description\":null,"
						+ "\"startDate\":\"2026-05-01\",\"endDate\":null,\"category\":\"OTHER\",\"private\":true}",
				owner.getId());
		String chJson =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, ownerBearer)
								.contentType(APPLICATION_JSON)
								.content(createChallenge))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long privateChallengeId = objectMapper.readTree(chJson).get("id").asLong();

		String inviteBody = String.format(
				"{\"inviteeEmail\":\"ci-pend-inv@test\",\"challengeId\":%d,\"subTaskId\":null,\"expiresAt\":null}",
				privateChallengeId);
		mockMvc.perform(post("/api/invites")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, ownerBearer)
						.contentType(APPLICATION_JSON)
						.content(inviteBody))
				.andExpect(status().isCreated());

		mockMvc.perform(
						get("/api/challenges/" + privateChallengeId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, inviteeBearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.private").value(true));

		String ownerCheckIn = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"2026-05-20\",\"subTaskId\":null}",
				owner.getId(), privateChallengeId);
		String ciJson =
				mockMvc.perform(post("/api/check-ins")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, ownerBearer)
								.contentType(APPLICATION_JSON)
								.content(ownerCheckIn))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long checkInId = objectMapper.readTree(ciJson).get("id").asLong();

		mockMvc.perform(
						get("/api/challenges/" + privateChallengeId + "/check-ins")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, inviteeBearer))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/check-ins/" + checkInId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, inviteeBearer))
				.andExpect(status().isNotFound());
	}
}
