package com.challenges.api.service;

import com.challenges.api.model.Invite;

public record InviteJoinAcceptResult(Invite invite, boolean participantInserted) {}
