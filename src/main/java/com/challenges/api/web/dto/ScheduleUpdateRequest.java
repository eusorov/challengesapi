package com.challenges.api.web.dto;

import com.challenges.api.model.ScheduleKind;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ScheduleUpdateRequest(@NotNull ScheduleKind kind, @Nullable List<String> weekDays) {}
