package com.challenges.api.web.dto;

import com.challenges.api.model.ScheduleKind;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScheduleUpdateRequest(@NotNull ScheduleKind kind, List<String> weekDays) {}
