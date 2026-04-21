package com.challenges.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.support.JwtLoginSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end API workflow: owner sets up a challenge (subtasks, schedules), invites another user,
 * that user <strong>accepts the challenge</strong> ({@code PUT /api/invites/{id}} with
 * {@code InviteStatus.ACCEPTED}), which creates a {@link com.challenges.api.model.Participant} — then both
 * users record check-ins.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeDomainWorkflowIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;

	private String bearerAuth;

	@Autowired
	ChallengeDomainWorkflowIT(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	@Test
	void ownerSetsUpChallenge_inviteeAcceptsChallenge_becomesParticipant_thenCheckIns() throws Exception {
		long ownerId = postUser("workflow-owner@example.test");
		long inviteeUserId = postUser("workflow-invitee@example.test");
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "workflow-owner@example.test", "password123");

		// --- Owner: challenge, subtasks, schedules ---
		long challengeId = postChallenge(ownerId, "Summer habit", LocalDate.of(2026, 7, 1));
		postSubTask(challengeId, "Morning block", 0);
		long subWeeklyId = postSubTask(challengeId, "Evening review", 1);

		long chScheduleId = postScheduleChallenge(challengeId);
		long stScheduleId = postScheduleSubTask(subWeeklyId);

		mockMvc.perform(get("/api/schedules/" + chScheduleId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("DAILY"))
				.andExpect(jsonPath("$.challengeId").value((int) challengeId));

		mockMvc.perform(get("/api/schedules/" + stScheduleId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kind").value("WEEKLY_ON_SELECTED_DAYS"))
				.andExpect(jsonPath("$.subTaskId").value((int) subWeeklyId))
				.andExpect(jsonPath("$.weekDays").isArray());

		mockMvc.perform(get("/api/challenges/" + challengeId + "/subtasks")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		// --- Invite (PENDING): owner asks invitee to join the challenge ---
		long inviteId = postInvite("workflow-invitee@example.test", challengeId, null);
		mockMvc.perform(get("/api/invites/" + inviteId)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.inviteeUserId").value((int) inviteeUserId));

		// --- Invitee accepts the challenge → becomes a Participant (challenge-wide scope) ---
		acceptInvite(inviteId);

		mockMvc.perform(get("/api/invites?challengeId=" + challengeId + "&role=SENT")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].inviteeUserId").value((int) inviteeUserId))
				.andExpect(jsonPath("$.content[0].status").value("ACCEPTED"));

		mockMvc.perform(get("/api/challenges/" + challengeId + "/participants")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].userId").value((int) ownerId))
				.andExpect(jsonPath("$.content[0].challengeId").value((int) challengeId))
				.andExpect(jsonPath("$.content[0].subTaskId").isEmpty())
				.andExpect(jsonPath("$.content[1].userId").value((int) inviteeUserId))
				.andExpect(jsonPath("$.content[1].challengeId").value((int) challengeId))
				.andExpect(jsonPath("$.content[1].subTaskId").isEmpty());

		// --- Both users log check-ins (each with their own JWT) ---
		postCheckIn(ownerId, challengeId, LocalDate.of(2026, 7, 10), null, bearerAuth);
		String inviteeBearer =
				JwtLoginSupport.bearerAuthorization(mockMvc, "workflow-invitee@example.test", "password123");
		postCheckIn(inviteeUserId, challengeId, LocalDate.of(2026, 7, 11), subWeeklyId, inviteeBearer);

		mockMvc.perform(get("/api/challenges/" + challengeId + "/check-ins")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].checkDate").value("2026-07-11"))
				.andExpect(jsonPath("$.content[0].subTaskId").value((int) subWeeklyId))
				.andExpect(jsonPath("$.content[1].checkDate").value("2026-07-10"))
				.andExpect(jsonPath("$.content[1].subTaskId").isEmpty());
	}

	private long postUser(String email) throws Exception {
		MvcResult res =
				mockMvc.perform(
								post("/api/users").header(HV, V1).contentType(APPLICATION_JSON).content(
										"{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
						.andExpect(status().isCreated())
						.andReturn();
		return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
	}

	private long postChallenge(long ownerUserId, String title, LocalDate startDate) throws Exception {
		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"%s\",\"description\":\"full workflow\",\""
						+ "startDate\":\"%s\",\"endDate\":null,\"category\":\"HEALTH_AND_FITNESS\"}",
				ownerUserId, title, startDate);
		MvcResult res =
				mockMvc.perform(post("/api/challenges")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.ownerUserId").value((int) ownerUserId))
						.andReturn();
		return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
	}

	private long postSubTask(long challengeId, String title, int sortIndex) throws Exception {
		String body = String.format(
				"{\"challengeId\":%d,\"title\":\"%s\",\"sortIndex\":%d}",
				challengeId, title, sortIndex);
		MvcResult res =
				mockMvc.perform(post("/api/subtasks")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andReturn();
		return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
	}

	private long postScheduleChallenge(long challengeId) throws Exception {
		String body = String.format(
				"{\"challengeId\":%d,\"kind\":\"DAILY\",\"weekDays\":[]}", challengeId);
		MvcResult res =
				mockMvc.perform(post("/api/schedules")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andReturn();
		return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
	}

	private long postScheduleSubTask(long subTaskId) throws Exception {
		String body =
				String.format(
						"{\"subTaskId\":%d,\"kind\":\"WEEKLY_ON_SELECTED_DAYS\",\"weekDays\":[\"MONDAY\",\"FRIDAY\"]}",
						subTaskId);
		MvcResult res =
				mockMvc.perform(post("/api/schedules")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andReturn();
		JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
		assertThat(node.get("weekDays")).isNotNull();
		return node.get("id").asLong();
	}

	private long postInvite(String inviteeEmail, long challengeId, Long subTaskId) throws Exception {
		String sub =
				subTaskId == null ? "null" : String.valueOf(subTaskId);
		String body = String.format(
				"{\"inviteeEmail\":\"%s\",\"challengeId\":%d,\"subTaskId\":%s}",
				inviteeEmail, challengeId, sub);
		MvcResult res =
				mockMvc.perform(post("/api/invites")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.status").value("PENDING"))
						.andReturn();
		return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
	}

	/** Invitee accepts the challenge: {@link com.challenges.api.model.InviteStatus#ACCEPTED}. */
	private void acceptInvite(long inviteId) throws Exception {
		mockMvc.perform(
						put("/api/invites/" + inviteId)
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth)
								.contentType(APPLICATION_JSON)
								.content("{\"status\":\"ACCEPTED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));
	}

	private void postCheckIn(
			long userId, long challengeId, LocalDate checkDate, Long subTaskId, String authorization)
			throws Exception {
		String st = subTaskId == null ? "null" : String.valueOf(subTaskId);
		String body = String.format(
				"{\"userId\":%d,\"challengeId\":%d,\"checkDate\":\"%s\",\"subTaskId\":%s}",
				userId, challengeId, checkDate, st);
		mockMvc.perform(post("/api/check-ins")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, authorization)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value((int) userId))
				.andExpect(jsonPath("$.challengeId").value((int) challengeId));
	}
}
