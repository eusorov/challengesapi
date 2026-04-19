package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class CurrentUserIT {

	private static final String API_VERSION = "API-Version";

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	CurrentUserIT(MockMvc mockMvc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Test
	void getUser_withBearer_returnsCurrentUserJson() throws Exception {
		userRepository.deleteAll();
		User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
		user.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
		userRepository.save(user);

		MvcResult login =
				mockMvc.perform(post("/api/login")
								.header(API_VERSION, "1")
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
						.andExpect(status().isOk())
						.andReturn();
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

		mockMvc.perform(get("/api/user").header(API_VERSION, "1").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("ada@example.com"))
				.andExpect(jsonPath("$.name").value("Ada"))
				.andExpect(jsonPath("$.role").value("user"));
	}

	@Test
	void getUser_withoutBearer_returns401() throws Exception {
		mockMvc.perform(get("/api/user").header(API_VERSION, "1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("Unauthorized"))
				.andExpect(jsonPath("$.detail").value("Authentication is required."));
	}
}
