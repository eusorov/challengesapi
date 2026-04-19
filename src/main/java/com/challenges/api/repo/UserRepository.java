package com.challenges.api.repo;

import com.challenges.api.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	@Query(value = "select u.id from User u order by u.id asc", countQuery = "select count(u) from User u")
	Page<Long> findIdsOrderByIdAsc(Pageable pageable);

	@Query("select u from User u where u.id in :ids order by u.id asc")
	List<User> findAllByIdInOrderByIdAsc(@Param("ids") Collection<Long> ids);
}
