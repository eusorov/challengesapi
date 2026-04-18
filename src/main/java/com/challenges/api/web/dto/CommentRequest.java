package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommentRequest(@NotNull Long userId, @NotBlank String body, Long subTaskId) {}
