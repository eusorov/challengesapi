package com.challenges.api.service;

import com.challenges.api.model.Participant;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record JoinChallengeOutcome(Participant participant, boolean created) {}
