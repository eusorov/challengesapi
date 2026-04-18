package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
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
class InviteControllerIT {

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

	private User inviter;
	private User invitee;
	private Challenge challenge;

	@BeforeEach
	void setup() {
		inviter = users.save(new User("inviter@test"));
		invitee = users.save(new User("invitee@test"));
		challenge =
				challenges.save(new Challenge(inviter, "invite-ch", null, LocalDate.of(2026, 4, 1), null));
	}

	@Test
	void createThenUpdateStatus() throws Exception {
		String body = String.format(
				"{\"inviterUserId\":%d,\"inviteeUserId\":%d,\"challengeId\":%d,"
						+ "\"subTaskId\":null,\"expiresAt\":null}",
				inviter.getId(), invitee.getId(), challenge.getId());

		String created =
				mockMvc.perform(post("/api/invites").header(HV, V1).contentType(APPLICATION_JSON).content(body))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.status").value("PENDING"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long inviteId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(
						get("/api/invites?challengeId=" + challenge.getId()).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(inviteId));

		String patch = "{\"status\":\"ACCEPTED\",\"expiresAt\":null}";
		mockMvc.perform(
						put("/api/invites/" + inviteId).header(HV, V1).contentType(APPLICATION_JSON).content(patch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));
	}
}
