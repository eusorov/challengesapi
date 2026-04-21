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
class InviteControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	InviteControllerIT(
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

	private User inviter;
	private User invitee;
	private Challenge challenge;
	private String bearerAuth;

	@Test
	void listInvites_requiresAuth() throws Exception {
		mockMvc.perform(get("/api/invites").header(HV, V1)).andExpect(status().isUnauthorized());
	}

	@Test
	void listInvites_receivedAndSent_scopeToCurrentUser() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,"
						+ "\"subTaskId\":null,\"expiresAt\":null}",
				challenge.getId());
		String created =
				mockMvc.perform(post("/api/invites")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long inviteId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(
						get("/api/invites?role=RECEIVED").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(
						get("/api/invites?role=SENT").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(inviteId));

		String bearerInvitee = JwtLoginSupport.bearerAuthorization(mockMvc, "invitee@test", "password");
		mockMvc.perform(
						get("/api/invites?role=RECEIVED").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerInvitee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(inviteId));

		mockMvc.perform(
						get("/api/invites?role=SENT").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerInvitee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@BeforeEach
	void setup() throws Exception {
		inviter = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "inviter@test"));
		invitee = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "invitee@test"));
		challenge =
				challenges.save(new Challenge(
						inviter, "invite-ch", null, LocalDate.of(2026, 4, 1), null, ChallengeCategory.OTHER));
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "inviter@test", "password");
	}

	@Test
	void createThenUpdateStatus() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,"
						+ "\"subTaskId\":null,\"expiresAt\":null}",
				challenge.getId());

		String created =
				mockMvc.perform(post("/api/invites").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.status").value("PENDING"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long inviteId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(
						get("/api/invites?challengeId=" + challenge.getId() + "&role=SENT")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(inviteId));

		String bearerInvitee = JwtLoginSupport.bearerAuthorization(mockMvc, "invitee@test", "password");
		mockMvc.perform(
						get("/api/invites?challengeId=" + challenge.getId())
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerInvitee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(inviteId));

		String patch = "{\"status\":\"ACCEPTED\",\"expiresAt\":null}";
		mockMvc.perform(
						put("/api/invites/" + inviteId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(patch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/participants").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId").value(invitee.getId().intValue()))
				.andExpect(jsonPath("$.content[0].challengeId").value(challenge.getId().intValue()));
	}

	@Test
	void subtaskScopedInviteAccept_createsScopedParticipant() throws Exception {
		SubTask sub = subTasks.save(new SubTask(challenge, "scoped step", 0));

		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,\"subTaskId\":%d}",
				challenge.getId(), sub.getId());

		String created =
				mockMvc.perform(post("/api/invites").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long inviteId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(
						put("/api/invites/" + inviteId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(
								"{\"status\":\"ACCEPTED\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/participants").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].userId").value(invitee.getId().intValue()))
				.andExpect(jsonPath("$.content[0].subTaskId").value(sub.getId().intValue()));
	}

	@Test
	void create_requiresAuth() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,\"subTaskId\":null}", challenge.getId());
		mockMvc.perform(post("/api/invites").header(HV, V1).contentType(APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void create_unknownInviteeEmail_notFound() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"missing-user@nowhere.test\",\"challengeId\":%d,\"subTaskId\":null}",
				challenge.getId());
		mockMvc.perform(post("/api/invites")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound());
	}

	@Test
	void create_selfInvite_forbidden() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"inviter@test\",\"challengeId\":%d,\"subTaskId\":null}", challenge.getId());
		mockMvc.perform(post("/api/invites")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void create_nonOwner_forbidden() throws Exception {
		User otherOwner = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "other-ch-owner@test"));
		Challenge otherChallenge =
				challenges.save(new Challenge(
						otherOwner,
						"not-inviter-ch",
						null,
						LocalDate.of(2026, 5, 1),
						null,
						ChallengeCategory.OTHER));
		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,\"subTaskId\":null}",
				otherChallenge.getId());
		mockMvc.perform(post("/api/invites")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void getInvite_byId_returnsInvite() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,\"subTaskId\":null,\"expiresAt\":null}",
				challenge.getId());
		String created =
				mockMvc.perform(post("/api/invites").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long inviteId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/invites/" + inviteId).header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(inviteId))
				.andExpect(jsonPath("$.challengeId").value(challenge.getId().intValue()))
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void getInvite_unknown_returnsNotFound() throws Exception {
		mockMvc.perform(
						get("/api/invites/999999999")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteInvite_removesRow() throws Exception {
		String body = String.format(
				"{\"inviteeEmail\":\"invitee@test\",\"challengeId\":%d,\"subTaskId\":null,\"expiresAt\":null}",
				challenge.getId());
		String created =
				mockMvc.perform(post("/api/invites").header(HV, V1).header(HttpHeaders.AUTHORIZATION, bearerAuth).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();
		long inviteId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(
						delete("/api/invites/" + inviteId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNoContent());
		mockMvc.perform(
						get("/api/invites/" + inviteId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteInvite_unknown_returnsNotFound() throws Exception {
		mockMvc.perform(
						delete("/api/invites/999999999")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNotFound());
	}
}
