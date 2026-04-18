package com.challenges.api.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CheckInUpdateRequest(@NotNull LocalDate checkDate, Long subTaskId) {}
