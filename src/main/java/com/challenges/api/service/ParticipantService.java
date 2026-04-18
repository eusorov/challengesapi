package com.challenges.api.service;

import com.challenges.api.model.Participant;
import com.challenges.api.repo.ParticipantRepository;
import java.util.List;
import org.springframework.lang.NonNull;
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
	public @NonNull List<Participant> listForChallenge(Long challengeId) {
		Assert.notNull(challengeId, "challengeId must not be null");
		return participants.findByChallenge_Id(challengeId);
	}
}
