package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentUpdateRequest(@NotBlank String body) {}
