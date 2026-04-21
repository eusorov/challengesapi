package com.challenges.api.service;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.Participant;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.storage.ChallengeImageStorage;
import com.challenges.api.support.ChallengeImagePaths;
import com.challenges.api.support.ChallengeLocationMapping;
import com.challenges.api.web.dto.ChallengeRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Service
public class ChallengeService {

	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final ParticipantRepository participants;
	private final ChallengeImageStorage challengeImageStorage;
	private final InviteService inviteService;

	public ChallengeService(
			UserRepository users,
			ChallengeRepository challenges,
			ParticipantRepository participants,
			ChallengeImageStorage challengeImageStorage,
			InviteService inviteService) {
		this.users = users;
		this.challenges = challenges;
		this.participants = participants;
		this.challengeImageStorage = challengeImageStorage;
		this.inviteService = inviteService;
	}

	@Transactional(readOnly = true)
	public @NonNull Page<Challenge> listChallenges(
			@NonNull Pageable pageable,
			@Nullable String q,
			@Nullable ChallengeCategory category,
			@Nullable String city) {
		Assert.notNull(pageable, "pageable must not be null");
		String searchTextLike = toSearchLikePattern(normalizeSearchText(q));
		String cityNormalized = normalizeCityFilter(city);
		String categoryName = category == null ? null : category.name();
		Page<Long> idPage =
				searchTextLike == null && categoryName == null && cityNormalized == null
						? challenges.findNonPrivateIdsOrderByIdAsc(pageable)
						: challenges.findNonPrivateIdsWithFilters(
								searchTextLike, categoryName, cityNormalized, pageable);
		if (idPage.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
		}
		List<Challenge> loaded = challenges.findAllWithSubtasksAndOwnerByIdIn(idPage.getContent());
		return new PageImpl<>(loaded, pageable, idPage.getTotalElements());
	}

	/**
	 * Loads a challenge for an HTTP GET. Public challenges are visible to anyone. Private challenges are visible only to
	 * the owner, any participant (challenge-wide or subtask-scoped), or a user with a usable pending invite (same rule
	 * as join). Missing or unauthorized private challenges yield empty (caller maps to 404).
	 */
	@Transactional(readOnly = true)
	public Optional<Challenge> findByIdForViewer(@NonNull Long id, @Nullable Long viewerUserId) {
		Assert.notNull(id, "id must not be null");
		Optional<Challenge> loaded = challenges.findByIdWithSubtasksAndOwner(id);
		if (loaded.isEmpty()) {
			return Optional.empty();
		}
		Challenge c = loaded.get();
		if (!c.isPrivate()) {
			return Optional.of(c);
		}
		if (viewerUserId == null) {
			return Optional.empty();
		}
		if (c.getOwner().getId().equals(viewerUserId)) {
			return Optional.of(c);
		}
		if (participants.existsByUser_IdAndChallenge_Id(viewerUserId, id)) {
			return Optional.of(c);
		}
		if (inviteService.hasUsablePendingInvite(viewerUserId, id)) {
			return Optional.of(c);
		}
		return Optional.empty();
	}

	@Transactional
	public Optional<Challenge> create(@NonNull ChallengeRequest req) {
		Assert.notNull(req, "request must not be null");
		boolean isPrivate = Boolean.TRUE.equals(req.isPrivate());
		return users.findById(req.ownerUserId()).map(owner -> {
			validateLocationRuleC(req);
			Challenge ch = new Challenge(
					owner,
					req.title(),
					req.description(),
					req.startDate(),
					req.endDate(),
					req.category(),
					isPrivate);
			applyLocationFromRequest(ch, req);
			Challenge saved = challenges.save(ch);
			Long oid = owner.getId();
			Long cid = saved.getId();
			if (!participants.existsByUser_IdAndChallenge_IdAndSubTaskIsNull(oid, cid)) {
				participants.save(new Participant(owner, saved));
			}
			return saved;
		});
	}

	@Transactional
	public Optional<Challenge> replace(@NonNull Long id, @NonNull ChallengeRequest req) {
		Assert.notNull(id, "id must not be null");
		Assert.notNull(req, "request must not be null");
		return users.findById(req.ownerUserId()).flatMap(owner -> challenges.findByIdWithSubtasksAndOwner(id).map(ch -> {
			validateLocationRuleC(req);
			ch.setOwner(owner);
			ch.setTitle(req.title());
			ch.setDescription(req.description());
			ch.setCategory(req.category());
			ch.setStartDate(req.startDate());
			ch.setEndDate(req.endDate());
			ch.setPrivate(Boolean.TRUE.equals(req.isPrivate()));
			applyLocationFromRequest(ch, req);
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
		return challenges.findByIdWithSubtasksAndOwner(challengeId).map(ch -> {
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
				PutObjectResponse response = challengeImageStorage.putObject(key, bytes, contentType);
				// check if the response is successful
				if (response.sdkHttpResponse().isSuccessful()) {
					ch.setImageObjectKey(key);
					return challenges.save(ch);
				} else {
					throw new IllegalStateException("Failed to upload image to S3");
				}
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	private static void validateLocationRuleC(ChallengeRequest req) {
		boolean hasCity = req.city() != null && !req.city().isBlank();
		boolean hasLoc = req.location() != null;
		if ((hasCity || hasLoc) && !hasLoc) {
			throw new IllegalArgumentException(
					"location with latitude and longitude is required when city or location is set");
		}
	}

	private static void applyLocationFromRequest(Challenge ch, ChallengeRequest req) {
		ch.setCity(normalizeCity(req.city()));
		ch.setLocation(req.location() != null ? ChallengeLocationMapping.toPoint(req.location()) : null);
	}

	private static String normalizeCity(String city) {
		if (city == null) {
			return null;
		}
		String trimmed = city.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static @Nullable String normalizeSearchText(@Nullable String q) {
		if (q == null) {
			return null;
		}
		String t = q.trim();
		return t.isEmpty() ? null : t;
	}

	/** LIKE pattern for case-insensitive title/description match; {@code null} means no text filter. */
	private static @Nullable String toSearchLikePattern(@Nullable String normalizedQ) {
		if (normalizedQ == null) {
			return null;
		}
		return "%" + normalizedQ + "%";
	}

	/** Lowercase trimmed city for repository comparison with {@code lower(trim(c.city))}. */
	private static @Nullable String normalizeCityFilter(@Nullable String city) {
		String n = normalizeCity(city);
		return n == null ? null : n.toLowerCase();
	}
}
