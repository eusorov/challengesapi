package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubTaskRequest(@NotNull Long challengeId, @NotBlank String title, int sortIndex) {}
