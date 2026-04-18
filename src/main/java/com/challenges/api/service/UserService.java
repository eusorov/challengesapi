package com.challenges.api.service;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<User> listUsers() {
		return users.findAll();
	}

	@Transactional(readOnly = true)
	public Optional<User> findById(Long id) {
		Assert.notNull(id, "id must not be null");
		return users.findById(id);
	}

	@Transactional
	public User create(String email) {
		Assert.notNull(email, "email must not be null");
		return users.save(new User(email));
	}

	@Transactional
	public Optional<User> replace(Long id, String email) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(email, "email must not be null");
		return users.findById(id).map(u -> {
			u.setEmail(email);
			return users.save(u);
		});
	}

	@Transactional
	public boolean delete(Long id) {
		Assert.notNull(id, "id must not be null");
		if (!users.existsById(id)) {
			return false;
		}
		users.deleteById(id);
		return true;
	}
}
