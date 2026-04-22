package com.challenges.api.service;

import com.challenges.api.model.Participant;

public record JoinChallengeOutcome(Participant participant, boolean created) {}
