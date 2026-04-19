package com.challenges.api.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.support.JwtLoginSupport;
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
class UserControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;

	@Autowired
	UserControllerIT(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void postThenListUsers() throws Exception {
		mockMvc.perform(post("/api/users")
						.header(HV, V1)
						.contentType(APPLICATION_JSON)
						.content("{\"email\":\"x@y.z\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("x@y.z"));

		String auth = JwtLoginSupport.bearerAuthorization(mockMvc, "x@y.z", "password123");
		mockMvc.perform(get("/api/users").header(HV, V1).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].email").value("x@y.z"));
	}

	@Test
	void getUnknownUserReturns404() throws Exception {
		mockMvc.perform(post("/api/users")
						.header(HV, V1)
						.contentType(APPLICATION_JSON)
						.content("{\"email\":\"probe@y.z\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		String auth = JwtLoginSupport.bearerAuthorization(mockMvc, "probe@y.z", "password123");
		mockMvc.perform(get("/api/users/999999").header(HV, V1).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isNotFound());
	}
}
