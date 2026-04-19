package com.challenges.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.Participant;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.support.JwtLoginSupport;
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
class ParticipantControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final ParticipantRepository participants;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	ParticipantControllerIT(
			MockMvc mockMvc,
			UserRepository users,
			ChallengeRepository challenges,
			ParticipantRepository participants,
			PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.challenges = challenges;
		this.participants = participants;
		this.passwordEncoder = passwordEncoder;
	}

	private Challenge challenge;
	private User participantUser;
	private String bearerAuth;

	@BeforeEach
	void setup() throws Exception {
		participantUser = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "part-user@test"));
		challenge =
				challenges.save(new Challenge(
						participantUser, "part-ch", null, LocalDate.of(2026, 6, 1), null, ChallengeCategory.OTHER));
		participants.save(new Participant(participantUser, challenge));
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "part-user@test", "password");
	}

	@Test
	void listParticipantsForChallenge() throws Exception {
		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/participants")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].challengeId").value(challenge.getId().intValue()))
				.andExpect(jsonPath("$.content[0].userId").value(participantUser.getId().intValue()));
	}
}
