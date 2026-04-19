package com.challenges.api.service;

import com.challenges.api.model.User;
import com.challenges.api.repo.UserRepository;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import static com.challenges.api.model.User.DEFAULT_ROLE;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public @NonNull Page<User> listUsers(@NonNull Pageable pageable) {
		Assert.notNull(pageable, "pageable must not be null");
		Page<Long> idPage = users.findIdsOrderByIdAsc(pageable);
		if (idPage.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
		}
		return new PageImpl<>(users.findAllByIdInOrderByIdAsc(idPage.getContent()), pageable, idPage.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Optional<User> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return users.findById(id);
	}

	@Transactional
	public @NonNull User create(@NonNull String email, @NonNull String password) {
		Assert.notNull(email, "email must not be null");
		return users.save(
				new User("User", email, passwordEncoder.encode(password), DEFAULT_ROLE));
	}

	@Transactional
	public Optional<User> replace(@NonNull Long id, @NonNull String email) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(email, "email must not be null");
		return users.findById(id).map(u -> {
			u.setEmail(email);
			return users.save(u);
		});
	}

	@Transactional
	public boolean delete(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		if (!users.existsById(id)) {
			return false;
		}
		users.deleteById(id);
		return true;
	}
}
