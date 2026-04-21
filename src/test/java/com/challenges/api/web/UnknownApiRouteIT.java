package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.support.JwtLoginSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnknownApiRouteIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;

	private String bearerAuth;

	@Autowired
	UnknownApiRouteIT(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@BeforeEach
	void registerAndLogin() throws Exception {
		mockMvc.perform(post("/api/users")
						.header(HV, V1)
						.contentType(APPLICATION_JSON)
						.content("{\"email\":\"unknown-route@test\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		bearerAuth = JwtLoginSupport.bearerAuthorization(mockMvc, "unknown-route@test", "password123");
	}

	@Test
	void unknownGetPathIsNot500() throws Exception {
		mockMvc.perform(
						get("/api/this-route-does-not-exist")
								.header(HV, V1)
								.header(HttpHeaders.AUTHORIZATION, bearerAuth))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void unknownPutPathIsNot500() throws Exception {
		mockMvc.perform(put("/api/this-route-does-not-exist")
						.header(HV, V1)
						.header(HttpHeaders.AUTHORIZATION, bearerAuth)
						.contentType(APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}
}
