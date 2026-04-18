package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SubTaskUpdateRequest(@NotBlank String title, int sortIndex) {}
