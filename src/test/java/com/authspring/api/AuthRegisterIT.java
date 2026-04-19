package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.service.RegisterService;
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
class AuthRegisterIT {

	private static final String API_VERSION = "API-Version";

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	AuthRegisterIT(MockMvc mockMvc, UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
	}

	@Test
	void registerReturnsMessageAndCanLogin() throws Exception {
		mockMvc.perform(multipart("/api/register")
						.param("name", "Ada")
						.param("email", "Ada@Example.com")
						.param("password", "newsecret12")
						.param("password_confirmation", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(RegisterService.SUCCESS_MESSAGE))
				.andExpect(jsonPath("$.token").isString())
				.andExpect(jsonPath("$.user.email").value("ada@example.com"))
				.andExpect(jsonPath("$.user.name").value("Ada"));

		mockMvc.perform(post("/api/login")
						.header(API_VERSION, "1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ada@example.com\",\"password\":\"newsecret12\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void registerDuplicateEmailReturns422() throws Exception {
		userRepository.save(
				new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), User.DEFAULT_ROLE));

		mockMvc.perform(multipart("/api/register")
						.param("name", "Bob")
						.param("email", "ada@example.com")
						.param("password", "newsecret12")
						.param("password_confirmation", "newsecret12")
						.header(API_VERSION, "1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]").value("The email has already been taken."));
	}
}
