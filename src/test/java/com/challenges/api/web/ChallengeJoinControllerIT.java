package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.support.JwtLoginSupport;
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
class ChallengeJoinControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	ChallengeJoinControllerIT(
			MockMvc mockMvc, UserRepository users, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
	}

	private User userA;
	private User userB;
	private String bearerA;
	private String bearerB;

	@BeforeEach
	void setup() throws Exception {
		userA = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "join-user-a@test"));
		userB = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "join-user-b@test"));
		bearerA = JwtLoginSupport.bearerAuthorization(mockMvc, "join-user-a@test", "password");
		bearerB = JwtLoginSupport.bearerAuthorization(mockMvc, "join-user-b@test", "password");
	}

	@Test
	void publicChallenge_joinCreatesParticipantThenIdempotentOk() throws Exception {
		String createBody = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Public join ch\",\"description\":null,"
						+ "\"startDate\":\"2026-01-01\",\"endDate\":null,\"category\":\"PRODUCTIVITY\"}",
				userA.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(userB.getId().intValue()))
				.andExpect(jsonPath("$.challengeId").value((int) challengeId))
				.andExpect(jsonPath("$.subTaskId").value((Object) null));

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userB.getId().intValue()))
				.andExpect(jsonPath("$.challengeId").value((int) challengeId));
	}

	@Test
	void ownerJoin_isIdempotentOk() throws Exception {
		String createBody = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Owner join ch\",\"description\":null,"
						+ "\"startDate\":\"2026-02-01\",\"endDate\":null,\"category\":\"OTHER\"}",
				userA.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userA.getId().intValue()))
				.andExpect(jsonPath("$.challengeId").value((int) challengeId));
	}

	@Test
	void privateChallenge_noInvite_forbidden() throws Exception {
		String createBody = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Private no invite\",\"description\":null,"
						+ "\"startDate\":\"2026-03-01\",\"endDate\":null,\"category\":\"LEARNING\",\"private\":true}",
				userA.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isForbidden());
	}

	@Test
	void privateChallenge_pendingInvite_acceptAndListsParticipant() throws Exception {
		String createBody = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Private with invite\",\"description\":null,"
						+ "\"startDate\":\"2026-04-01\",\"endDate\":null,\"category\":\"OTHER\",\"private\":true}",
				userA.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		String inviteBody = String.format(
				"{\"inviteeEmail\":\"join-user-b@test\",\"challengeId\":%d,"
						+ "\"subTaskId\":null,\"expiresAt\":null}",
				challengeId);

		String inviteCreated =
				mockMvc.perform(post("/api/invites")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(inviteBody))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.status").value("PENDING"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long inviteId = objectMapper.readTree(inviteCreated).get("id").asLong();

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(userB.getId().intValue()));

		mockMvc.perform(get("/api/invites/" + inviteId).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mockMvc.perform(get("/api/challenges/" + challengeId + "/participants")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[1].userId").value(userB.getId().intValue()));
	}

	@Test
	void expiredPendingInvite_forbidden() throws Exception {
		String createBody = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Expired invite ch\",\"description\":null,"
						+ "\"startDate\":\"2026-05-01\",\"endDate\":null,\"category\":\"OTHER\",\"private\":true}",
				userA.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		String inviteBody = String.format(
				"{\"inviteeEmail\":\"join-user-b@test\",\"challengeId\":%d,"
						+ "\"subTaskId\":null,\"expiresAt\":\"2020-01-01T00:00:00Z\"}",
				challengeId);

		mockMvc.perform(post("/api/invites")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerA)
						.contentType(APPLICATION_JSON)
						.content(inviteBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isForbidden());
	}

	@Test
	void twoPendingInvites_acceptsOldestById() throws Exception {
		String createBody = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Two invites ch\",\"description\":null,"
						+ "\"startDate\":\"2026-06-01\",\"endDate\":null,\"category\":\"PRODUCTIVITY\",\"private\":true}",
				userA.getId());

		String created =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(createBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long challengeId = objectMapper.readTree(created).get("id").asLong();

		String inviteBody = String.format(
				"{\"inviteeEmail\":\"join-user-b@test\",\"challengeId\":%d,"
						+ "\"subTaskId\":null,\"expiresAt\":null}",
				challengeId);

		String first =
				mockMvc.perform(post("/api/invites")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(inviteBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long inviteId1 = objectMapper.readTree(first).get("id").asLong();

		String second =
				mockMvc.perform(post("/api/invites")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerA)
								.contentType(APPLICATION_JSON)
								.content(inviteBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long inviteId2 = objectMapper.readTree(second).get("id").asLong();

		mockMvc.perform(post("/api/challenges/" + challengeId + "/join")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerB))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/invites/" + inviteId1).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mockMvc.perform(get("/api/invites/" + inviteId2).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));
	}
}
