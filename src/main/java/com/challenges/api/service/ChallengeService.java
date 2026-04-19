package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.storage.ChallengeImageStorage;
import com.challenges.api.support.ChallengeImagePaths;
import com.challenges.api.web.dto.ChallengeRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChallengeService {

	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final ChallengeImageStorage challengeImageStorage;

	public ChallengeService(
			UserRepository users, ChallengeRepository challenges, ChallengeImageStorage challengeImageStorage) {
		this.users = users;
		this.challenges = challenges;
		this.challengeImageStorage = challengeImageStorage;
	}

	@Transactional(readOnly = true)
	public @NonNull List<Challenge> listChallenges() {
		return challenges.findAllWithOwner();
	}

	@Transactional(readOnly = true)
	public Optional<Challenge> findById(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		return challenges.findByIdWithOwner(id);
	}

	@Transactional
	public Optional<Challenge> create(@NonNull ChallengeRequest req) {
		Assert.notNull(req, "request must not be null");
		return users.findById(req.ownerUserId()).map(owner -> challenges.save(new Challenge(owner,
						req.title(),
						req.description(),
						req.startDate(),
						req.endDate())));
	}

	@Transactional
	public Optional<Challenge> replace(@NonNull Long id, @NonNull ChallengeRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return users.findById(req.ownerUserId()).flatMap(owner -> challenges.findByIdWithOwner(id).map(ch -> {
			ch.setOwner(owner);
			ch.setTitle(req.title());
			ch.setDescription(req.description());
			ch.setStartDate(req.startDate());
			ch.setEndDate(req.endDate());
			return challenges.save(ch);
		}));
	}

	@Transactional
	public boolean delete(@NonNull Long id) {
		Assert.notNull(id, "id must not be null");
		if (!challenges.existsById(id)) {
			return false;
		}
		challenges.deleteById(id);
		return true;
	}

	@Transactional
	public Optional<Challenge> uploadImage(
			@NonNull Long challengeId, @NonNull MultipartFile file, long currentUserId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(file, "file must not be null");
		return challenges.findByIdWithOwner(challengeId).map(ch -> {
			if (!ch.getOwner().getId().equals(currentUserId)) {
				throw new AccessDeniedException("Not challenge owner");
			}
			String contentType = file.getContentType();
			if (contentType == null
					|| !(contentType.equals("image/jpeg")
							|| contentType.equals("image/png")
							|| contentType.equals("image/webp"))) {
				throw new IllegalArgumentException("Only image/jpeg, image/png, image/webp allowed");
			}
			String key = ChallengeImagePaths.objectKey(ch, file.getOriginalFilename());
			try {
				byte[] bytes = file.getBytes();
				if (bytes.length == 0) {
					throw new IllegalArgumentException("Empty file");
				}
				challengeImageStorage.putObject(key, bytes, contentType);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			ch.setImageObjectKey(key);
			return challenges.save(ch);
		});
	}
}
