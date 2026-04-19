package com.challenges.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.challenges.api.model.ChallengeCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeCategoryControllerIT {

	private static final String HV = "API-Version";
	private static final String V1 = "1";

	private final MockMvc mockMvc;

	@Autowired
	ChallengeCategoryControllerIT(MockMvc mockMvc) {
		this.mockMvc = mockMvc;
	}

	@Test
	void listReturnsAllCategoriesInOrder() throws Exception {
		ChallengeCategory[] categories = ChallengeCategory.values();
		ResultActions ra =
				mockMvc.perform(get("/api/categories").header(HV, V1))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.length()").value(categories.length));
		for (int i = 0; i < categories.length; i++) {
			ra = ra.andExpect(jsonPath("$[" + i + "]").value(categories[i].name()));
		}
	}
}
