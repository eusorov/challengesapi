package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class UserRepositoryTest {

	private final UserRepository userRepository;

	@Autowired
	UserRepositoryTest(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Test
	void savesAndFindsUserById() {
		User saved = userRepository.save(new User("pat@example.com"));
		assertThat(saved.getId()).isNotNull();
		assertThat(userRepository.findById(saved.getId())).get().extracting(User::getEmail).isEqualTo("pat@example.com");
	}
}
