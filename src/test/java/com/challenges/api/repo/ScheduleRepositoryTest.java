package com.challenges.api.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScheduleRepositoryTest {

	private final TestEntityManager entityManager;
	private final ScheduleRepository scheduleRepository;

	@Autowired
	ScheduleRepositoryTest(TestEntityManager entityManager, ScheduleRepository scheduleRepository) {
		this.entityManager = entityManager;
		this.scheduleRepository = scheduleRepository;
	}

	@Test
	void persistsChallengeScheduleAndSubtaskSchedule() {
		User u = entityManager.persistAndFlush(new User("sched-user@example.com"));
		Challenge ch = entityManager.persistAndFlush(new Challenge(u, "Scheduled", null, LocalDate.of(2026, 5, 1), null));

		Schedule chSch = Schedule.forChallenge(ch, ScheduleKind.DAILY, List.of());
		ch.bindSchedule(chSch);
		scheduleRepository.save(chSch);

		SubTask st = entityManager.persistAndFlush(new SubTask(ch, "Sub with cadence", 1));
		Schedule stSch =
				Schedule.forSubTask(st, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
		st.bindSchedule(stSch);
		scheduleRepository.save(stSch);

		entityManager.flush();
		entityManager.clear();

		Schedule loadedCh = scheduleRepository.findById(chSch.getId()).orElseThrow();
		assertThat(loadedCh.getChallenge().getId()).isEqualTo(ch.getId());
		assertThat(loadedCh.getSubTask()).isNull();
		assertThat(loadedCh.getKind()).isEqualTo(ScheduleKind.DAILY);

		Schedule loadedSt = scheduleRepository.findById(stSch.getId()).orElseThrow();
		assertThat(loadedSt.getSubTask().getId()).isEqualTo(st.getId());
		assertThat(loadedSt.getChallenge()).isNull();
		assertThat(loadedSt.getKind()).isEqualTo(ScheduleKind.WEEKLY_ON_SELECTED_DAYS);
		assertThat(loadedSt.getWeekDays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
	}
}
