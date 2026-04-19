package com.challenges.api.service;

import com.challenges.api.model.Participant;
import com.challenges.api.repo.ParticipantRepository;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class ParticipantService {

	private final ParticipantRepository participants;

	public ParticipantService(ParticipantRepository participants) {
		this.participants = participants;
	}

	@Transactional(readOnly = true)
	public @NonNull Page<Participant> listForChallenge(@NonNull Long challengeId, @NonNull Pageable pageable) {
		Assert.notNull(challengeId, "challengeId must not be null");
		Assert.notNull(pageable, "pageable must not be null");
		Page<Long> idPage = participants.findIdsForChallengeOrderByIdAsc(challengeId, pageable);
		if (idPage.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
		}
		return new PageImpl<>(
				participants.findByIdInWithAssociations(idPage.getContent()), pageable, idPage.getTotalElements());
	}
}
