package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthLoginIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
		userRepository.save(user);
	}

	@Test
	void loginReturnsTokenAndUser() throws Exception {
		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.user.email").value("ada@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ada"));
	}

	@Test
	void loginWithFormUrlEncodedReturnsTokenAndUser() throws Exception {
		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("email", "ada@example.com")
						.param("password", "secret"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.user.email").value("ada@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ada"));
	}

	@Test
	void loginWrongPasswordReturns422() throws Exception {
		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]").value("The provided credentials are incorrect."));
	}
}
