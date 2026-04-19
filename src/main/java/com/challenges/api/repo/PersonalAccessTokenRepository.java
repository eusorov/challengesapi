package com.challenges.api.repo;

import com.challenges.api.model.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {}
