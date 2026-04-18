package com.challenges.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Participant;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.UserRepository;
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
class ParticipantControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private ChallengeRepository challenges;

	@Autowired
	private ParticipantRepository participants;

	private Challenge challenge;
	private User participantUser;

	@BeforeEach
	void setup() {
		participantUser = users.save(new User("part-user@test"));
		challenge =
				challenges.save(new Challenge(participantUser, "part-ch", null, LocalDate.of(2026, 6, 1), null));
		participants.save(new Participant(participantUser, challenge));
	}

	@Test
	void listParticipantsForChallenge() throws Exception {
		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/participants").header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].challengeId").value(challenge.getId().intValue()))
				.andExpect(jsonPath("$[0].userId").value(participantUser.getId().intValue()));
	}
}
