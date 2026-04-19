package com.challenges.api.web;

import com.challenges.api.model.ChallengeCategory;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/categories", version = "1")
public class ChallengeCategoryController {

	@GetMapping
	public List<ChallengeCategory> list() {
		return Arrays.stream(ChallengeCategory.values()).toList();
	}
}
