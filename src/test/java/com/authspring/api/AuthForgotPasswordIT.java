package com.authspring.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.ChallengesApiApplication;
import com.challenges.api.model.User;
import com.challenges.api.repo.PasswordResetTokenRepository;
import com.challenges.api.repo.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = ChallengesApiApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthForgotPasswordIT.MockMailSenderConfig.class)
@Transactional
class AuthForgotPasswordIT {

	private static final String API_VERSION = "API-Version";

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JavaMailSender javaMailSender;

	@Autowired
	AuthForgotPasswordIT(
			MockMvc mockMvc,
			UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordEncoder passwordEncoder,
			JavaMailSender javaMailSender) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.javaMailSender = javaMailSender;
	}

	@TestConfiguration
	static class MockMailSenderConfig {

		@Bean
		@Primary
		JavaMailSender javaMailSender() {
			JavaMailSender mock = Mockito.mock(JavaMailSender.class);
			Session session = Session.getInstance(new Properties());
			MimeMessage mimeMessage = new MimeMessage(session);
			Mockito.when(mock.createMimeMessage()).thenReturn(mimeMessage);
			return mock;
		}
	}

	@BeforeEach
	void setUp() {
		passwordResetTokenRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
	}

	@Test
	void forgotPasswordSendsMailAndPersistsToken() throws Exception {
		mockMvc.perform(multipart("/api/forgot-password")
						.param("email", "ada@example.com")
						.header(API_VERSION, "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("We have e-mailed your password reset link!"));

		verify(javaMailSender).send(any(MimeMessage.class));
		Assertions.assertTrue(passwordResetTokenRepository.findById("ada@example.com").isPresent());
	}

	@Test
	void forgotPasswordUnknownUserReturns422() throws Exception {
		mockMvc.perform(multipart("/api/forgot-password")
						.param("email", "nobody@example.com")
						.header(API_VERSION, "1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errors.email[0]")
						.value("We can't find a user with that e-mail address."));
	}
}
