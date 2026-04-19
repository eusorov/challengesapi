package com.challenges.api.repo;

import com.challenges.api.model.PersonalAccessToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

	Optional<PersonalAccessToken> findByToken(String token);

	void deleteByToken(String token);
}
