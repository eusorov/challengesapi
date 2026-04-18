package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record CommentRequest(@NotNull Long userId, @NotBlank String body, @Nullable Long subTaskId) {}
