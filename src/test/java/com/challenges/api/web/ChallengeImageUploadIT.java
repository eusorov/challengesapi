package com.challenges.api.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.storage.ChallengeImageStorage;
import com.challenges.api.support.JwtLoginSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeImageUploadIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	@MockitoBean
	private ChallengeImageStorage challengeImageStorage;

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ObjectMapper objectMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	ChallengeImageUploadIT(
			MockMvc mockMvc, UserRepository users, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.objectMapper = objectMapper;
		this.passwordEncoder = passwordEncoder;
	}

	private User owner1;
	private User owner2;
	private String bearerOwner1;
	private String bearerOwner2;
	private long challengeId;

	@BeforeEach
	void setup() throws Exception {
		owner1 = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "img-owner1@test"));
		owner2 = users.save(JwtLoginSupport.userWithLoginPassword(passwordEncoder, "img-owner2@test"));
		bearerOwner1 = JwtLoginSupport.bearerAuthorization(mockMvc, "img-owner1@test", "password");
		bearerOwner2 = JwtLoginSupport.bearerAuthorization(mockMvc, "img-owner2@test", "password");

		String body = String.format(
				"{\"ownerUserId\":%d,\"title\":\"Summer Pic\",\"description\":null,"
						+ "\"startDate\":\"2026-01-01\",\"endDate\":null}",
				owner1.getId());
		String created = mockMvc.perform(post("/api/challenges")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerOwner1)
						.contentType(APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode node = objectMapper.readTree(created);
		challengeId = node.get("id").asLong();
	}

	@Test
	void ownerUploadsImage_returnsKeyAndUrl() throws Exception {
		var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {10, 20, 30});

		mockMvc.perform(multipart("/api/challenges/" + challengeId + "/image")
						.file(file)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerOwner1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageObjectKey")
						.value(matchesPattern("^[a-z0-9-]+-\\d+/[a-zA-Z0-9._-]+\\.[a-z]+$")))
				.andExpect(jsonPath("$.imageUrl").value(startsWith("https://example.com/bucket/")));

		Mockito.verify(challengeImageStorage).putObject(any(), any(), eq("image/jpeg"));
	}

	@Test
	void nonOwnerUploads_forbidden() throws Exception {
		var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});

		mockMvc.perform(multipart("/api/challenges/" + challengeId + "/image")
						.file(file)
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerOwner2))
				.andExpect(status().isForbidden());
	}
}
