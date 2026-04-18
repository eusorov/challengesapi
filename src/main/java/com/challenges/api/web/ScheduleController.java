package com.challenges.api.web;

import com.challenges.api.web.dto.ScheduleCreateRequest;
import com.challenges.api.web.dto.ScheduleResponse;
import com.challenges.api.web.dto.ScheduleUpdateRequest;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/schedules", version = "1")
public class ScheduleController {

	private final ScheduleService scheduleService;

	public ScheduleController(ScheduleService scheduleService) {
		this.scheduleService = scheduleService;
	}

	@GetMapping("/{id}")
	public ResponseEntity<ScheduleResponse> get(@PathVariable Long id) {
		return scheduleService.findById(id)
				.map(ScheduleResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleCreateRequest req) {
		List<DayOfWeek> days = ScheduleService.parseWeekDays(req.weekDays());
		if (req.challengeId() != null) {
			return scheduleService
					.createForChallenge(req.challengeId(), req.kind(), days)
					.map(ScheduleResponse::from)
					.map(s -> ResponseEntity.status(HttpStatus.CREATED).body(s))
					.orElse(ResponseEntity.notFound().build());
		}
		return scheduleService
				.createForSubTask(req.subTaskId(), req.kind(), days)
				.map(ScheduleResponse::from)
				.map(s -> ResponseEntity.status(HttpStatus.CREATED).body(s))
				.orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<ScheduleResponse> replace(
			@PathVariable Long id, @Valid @RequestBody ScheduleUpdateRequest req) {
		List<DayOfWeek> days = ScheduleService.parseWeekDays(req.weekDays());
		return scheduleService
				.update(id, req.kind(), days)
				.map(ScheduleResponse::from)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!scheduleService.delete(id)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.noContent().build();
	}
}
