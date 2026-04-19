package com.authspring.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthEmailVerificationNotificationIT.MockMailSenderConfig.class)
@Transactional
class AuthEmailVerificationNotificationIT {

	private static final String API_VERSION = "API-Version";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JavaMailSender javaMailSender;

	@TestConfiguration
	static class MockMailSenderConfig {

		@Bean
		@Primary
		JavaMailSender javaMailSender() {
			JavaMailSender mail = mock(JavaMailSender.class);
			Session session = Session.getInstance(new Properties());
			MimeMessage mimeMessage = new MimeMessage(session);
			when(mail.createMimeMessage()).thenReturn(mimeMessage);
			return mail;
		}
	}

	@BeforeEach
	void setUp() {
		clearInvocations(javaMailSender);
		userRepository.deleteAll();
		userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
	}

	@Test
	void sendsVerificationWhenUnverified() throws Exception {
		String token = loginToken();

		mockMvc.perform(
						multipart("/api/email/verification-notification")
								.header(API_VERSION, "1")
								.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("verification-link-sent"));

		verify(javaMailSender).send(any(MimeMessage.class));
	}

	@Test
	void alreadyVerifiedReturns409() throws Exception {
		User u = userRepository.findByEmail("ada@example.com").orElseThrow();
		u.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
		userRepository.save(u);

		String token = loginToken();

		mockMvc.perform(
						multipart("/api/email/verification-notification")
								.header(API_VERSION, "1")
								.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value("email-already-verified"));

		verify(javaMailSender, never()).send(any(MimeMessage.class));
	}

	@Test
	void withoutTokenReturns401() throws Exception {
		mockMvc.perform(multipart("/api/email/verification-notification").header(API_VERSION, "1"))
				.andExpect(status().isUnauthorized());
	}

	private String loginToken() throws Exception {
		MvcResult login =
				mockMvc.perform(
								post("/api/login")
										.header(API_VERSION, "1")
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
						.andExpect(status().isOk())
						.andReturn();
		return JsonPath.read(login.getResponse().getContentAsString(), "$.token");
	}
}
