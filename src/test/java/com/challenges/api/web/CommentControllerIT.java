package com.challenges.api.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;
	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ObjectMapper objectMapper;

	@Autowired
	CommentControllerIT(
			MockMvc mockMvc,
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.objectMapper = objectMapper;
	}

	private User owner;
	private User commenter;
	private Challenge challenge;

	@BeforeEach
	void setup() {
		owner = users.save(User.forTest("owner-comments@test"));
		commenter = users.save(User.forTest("commenter@test"));
		challenge =
				challenges.save(new Challenge(owner, "ch-comments", null, LocalDate.of(2026, 4, 1), null));
	}

	@Test
	void postChallengeWideThenList_hasNullSubTaskId() throws Exception {
		String postBody =
				String.format("{\"userId\":%d,\"body\":\"hello challenge\",\"subTaskId\":null}", commenter.getId());

		String created =
				mockMvc.perform(
								post("/api/challenges/" + challenge.getId() + "/comments")
										.header(HV, V1)
										.contentType(APPLICATION_JSON)
										.content(postBody))
						.andExpect(status().isCreated())
						.andExpect(jsonPath("$.subTaskId").value(nullValue()))
						.andExpect(jsonPath("$.body").value("hello challenge"))
						.andReturn()
						.getResponse()
						.getContentAsString();

		long commentId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/comments").header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(commentId))
				.andExpect(jsonPath("$[0].subTaskId").value(nullValue()))
				.andExpect(jsonPath("$[0].body").value("hello challenge"));
	}

	@Test
	void postSubTaskComment_filterList_andFullList() throws Exception {
		SubTask sub = subTasks.save(new SubTask(challenge, "step one", 0));

		String wide =
				String.format("{\"userId\":%d,\"body\":\"wide\",\"subTaskId\":null}", commenter.getId());
		mockMvc.perform(
						post("/api/challenges/" + challenge.getId() + "/comments")
								.header(HV, V1)
								.contentType(APPLICATION_JSON)
								.content(wide))
				.andExpect(status().isCreated());

		String scoped =
				String.format(
						"{\"userId\":%d,\"body\":\"on sub\",\"subTaskId\":%d}",
						commenter.getId(), sub.getId());
		mockMvc.perform(
						post("/api/challenges/" + challenge.getId() + "/comments")
								.header(HV, V1)
								.contentType(APPLICATION_JSON)
								.content(scoped))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subTaskId").value(sub.getId().intValue()));

		mockMvc.perform(
						get("/api/challenges/" + challenge.getId() + "/comments?subTaskId=" + sub.getId())
								.header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].body").value("on sub"));

		mockMvc.perform(get("/api/challenges/" + challenge.getId() + "/comments").header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void putThenDelete_getReturns404() throws Exception {
		String postBody =
				String.format("{\"userId\":%d,\"body\":\"orig\",\"subTaskId\":null}", commenter.getId());
		String created =
				mockMvc.perform(
								post("/api/challenges/" + challenge.getId() + "/comments")
										.header(HV, V1)
										.contentType(APPLICATION_JSON)
										.content(postBody))
						.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString();

		long commentId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(
						put("/api/comments/" + commentId)
								.header(HV, V1)
								.contentType(APPLICATION_JSON)
								.content("{\"body\":\"updated\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").value("updated"));

		mockMvc.perform(get("/api/comments/" + commentId).header(HV, V1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.body").value("updated"));

		mockMvc.perform(delete("/api/comments/" + commentId).header(HV, V1))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/comments/" + commentId).header(HV, V1)).andExpect(status().isNotFound());
	}

	@Test
	void postWithUnknownUser_returns404() throws Exception {
		String body = "{\"userId\":999999,\"body\":\"x\",\"subTaskId\":null}";
		mockMvc.perform(
						post("/api/challenges/" + challenge.getId() + "/comments")
								.header(HV, V1)
								.contentType(APPLICATION_JSON)
								.content(body))
				.andExpect(status().isNotFound());
	}
}
