package com.challenges.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.Participant;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.ScheduleRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DomainBulkFixtureIT {

	private static final List<DayOfWeek> MON_TUE_FRI =
			List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);

	private final UserRepository userRepository;
	private final ChallengeRepository challengeRepository;
	private final SubTaskRepository subTaskRepository;
	private final ParticipantRepository participantRepository;
	private final ScheduleRepository scheduleRepository;

	@Autowired
	DomainBulkFixtureIT(
			UserRepository userRepository,
			ChallengeRepository challengeRepository,
			SubTaskRepository subTaskRepository,
			ParticipantRepository participantRepository,
			ScheduleRepository scheduleRepository) {
		this.userRepository = userRepository;
		this.challengeRepository = challengeRepository;
		this.subTaskRepository = subTaskRepository;
		this.participantRepository = participantRepository;
		this.scheduleRepository = scheduleRepository;
	}

	@Test
	void bulkFixture_countsAndSchedules() {
		List<User> users = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			users.add(userRepository.save(User.forTest("u" + i + "@fixture.test")));
		}

		List<Challenge> challenges = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			User owner = users.get(i / 2);
			LocalDate start = LocalDate.of(2026, 1, 1).plusDays(i);
			challenges.add(challengeRepository.save(
					new Challenge(owner, "ch" + i, null, start, null, ChallengeCategory.OTHER, null, null, false)));
		}

		for (int c = 0; c < 10; c++) {
			for (int d = 0; d < 5; d++) {
				participantRepository.save(new Participant(users.get((c + d) % 10), challenges.get(c)));
			}
		}

		for (int c = 0; c < 10; c++) {
			Challenge ch = challenges.get(c);
			boolean chDaily = (c % 2 == 0);
			Schedule chSch =
					chDaily
							? Schedule.forChallenge(ch, ScheduleKind.DAILY, List.of())
							: Schedule.forChallenge(ch, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, MON_TUE_FRI);
			ch.bindSchedule(chSch);
			scheduleRepository.save(chSch);
		}

		for (int c = 1; c < 10; c++) {
			for (int j = 0; j < 10; j++) {
				SubTask st = subTaskRepository.save(new SubTask(challenges.get(c), "st-" + j, j));
				boolean stDaily = ((c + j) % 2) == 0;
				Schedule stSch =
						stDaily
								? Schedule.forSubTask(st, ScheduleKind.DAILY, List.of())
								: Schedule.forSubTask(st, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, MON_TUE_FRI);
				st.bindSchedule(stSch);
				scheduleRepository.save(stSch);
			}
		}

		assertThat(userRepository.count()).isEqualTo(10);
		assertThat(challengeRepository.count()).isEqualTo(10);
		assertThat(participantRepository.count()).isEqualTo(50);
		assertThat(subTaskRepository.count()).isEqualTo(90);
		assertThat(scheduleRepository.count()).isEqualTo(100);

		long dailyCount = scheduleRepository.findAll().stream().filter(s -> s.getKind() == ScheduleKind.DAILY).count();
		long weeklyCount =
				scheduleRepository.findAll().stream().filter(s -> s.getKind() == ScheduleKind.WEEKLY_ON_SELECTED_DAYS).count();
		// Challenge schedules: c even -> 5 DAILY, c odd -> 5 WEEKLY. Subtask: (c+j)%2 == 0 -> DAILY; 45 each kind for 90 subtasks.
		assertThat(dailyCount).isEqualTo(50);
		assertThat(weeklyCount).isEqualTo(50);
	}
}
