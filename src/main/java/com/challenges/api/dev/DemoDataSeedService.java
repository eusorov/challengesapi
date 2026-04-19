package com.challenges.api.dev;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.CheckIn;
import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import com.challenges.api.model.Participant;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.InviteRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.ScheduleRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo-seed")
public class DemoDataSeedService {

	static final String SEED_EMAIL_1 = "seed01@demo.local";
	private static final List<DayOfWeek> MON_WED_FRI =
			List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
	private static final List<DayOfWeek> TUE_THU = List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
	private static final List<DayOfWeek> WEEKDAYS = List.of(
			DayOfWeek.MONDAY,
			DayOfWeek.TUESDAY,
			DayOfWeek.WEDNESDAY,
			DayOfWeek.THURSDAY,
			DayOfWeek.FRIDAY);
	private static final List<DayOfWeek> SAT_SUN = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ScheduleRepository schedules;
	private final ParticipantRepository participants;
	private final CheckInRepository checkIns;
	private final InviteRepository invites;

	public DemoDataSeedService(
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ScheduleRepository schedules,
			ParticipantRepository participants,
			CheckInRepository checkIns,
			InviteRepository invites) {
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.schedules = schedules;
		this.participants = participants;
		this.checkIns = checkIns;
		this.invites = invites;
	}

	@Transactional
	public void seedIfEmpty() {
		if (users.existsByEmail(SEED_EMAIL_1)) {
			return;
		}

		List<User> seedUsers = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			String n = i < 10 ? "0" + i : "10";
			seedUsers.add(users.save(new User(
					"Seed User " + n,
					"seed" + n + "@demo.local",
					User.TEST_PASSWORD_HASH,
					User.DEFAULT_ROLE)));
		}

		String[] descriptions = {
			"Morning runs before work — build the habit slowly.",
			"Read 20 pages of non-fiction every day.",
			"No sugar: whole-team wellness challenge for April.",
			"Meditation streak: 10 minutes minimum.",
			"Hydration: 8 glasses — track with the group.",
			"Strength training 3 times per week at the gym.",
			"Learn Spanish vocabulary — 15 new words daily.",
			"Sleep before 11pm — recovery focus.",
			"Walk 8k steps — office group challenge.",
			"Journal one page each evening for mindfulness."
		};

		LocalDate base = LocalDate.of(2026, 4, 1);
		int[] ownerIndex = {0, 0, 1, 2, 2, 3, 4, 5, 6, 7};
		List<Challenge> chList = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			User owner = seedUsers.get(ownerIndex[i]);
			LocalDate start = base.plusDays(i * 2L);
			LocalDate end = i % 3 == 0 ? start.plusMonths(2) : null;
			chList.add(challenges.save(new Challenge(
					owner, "Demo Challenge " + (i + 1), descriptions[i], start, end)));
		}

		List<List<DayOfWeek>> challengeWeekPatterns = List.of(
				List.of(),
				MON_WED_FRI,
				List.of(),
				TUE_THU,
				WEEKDAYS,
				List.of(),
				SAT_SUN,
				List.of(),
				MON_WED_FRI,
				List.of());
		ScheduleKind[] challengeKinds = {
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY,
			ScheduleKind.WEEKLY_ON_SELECTED_DAYS,
			ScheduleKind.DAILY
		};

		for (int i = 0; i < 10; i++) {
			Challenge ch = chList.get(i);
			Schedule sch =
					Schedule.forChallenge(ch, challengeKinds[i], challengeWeekPatterns.get(i));
			ch.bindSchedule(sch);
			schedules.save(sch);
		}

		int[] participantCount = {5, 2, 4, 1, 3, 5, 2, 4, 1, 3};
		for (int c = 0; c < 10; c++) {
			Challenge ch = chList.get(c);
			int ownerIdx = ownerIndex[c];
			int n = participantCount[c];
			for (int k = 0; k < n; k++) {
				int uIdx = (ownerIdx + 1 + k) % 10;
				if (uIdx == ownerIdx) {
					uIdx = (uIdx + 1) % 10;
				}
				participants.save(new Participant(seedUsers.get(uIdx), ch));
			}
		}

		int[] subtaskCounts = {2, 0, 1, 3, 0, 1, 2, 0, 1, 0};
		List<List<SubTask>> subtasksByChallenge = new ArrayList<>();
		for (int c = 0; c < 10; c++) {
			subtasksByChallenge.add(new ArrayList<>());
			Challenge ch = chList.get(c);
			for (int s = 0; s < subtaskCounts[c]; s++) {
				SubTask st = subTasks.save(new SubTask(ch, "Subtask " + (s + 1) + " of Ch " + (c + 1), s));
				subtasksByChallenge.get(c).add(st);
				boolean stDaily = (c + s) % 2 == 0;
				Schedule stSch =
						stDaily
								? Schedule.forSubTask(st, ScheduleKind.DAILY, List.of())
								: Schedule.forSubTask(
										st, ScheduleKind.WEEKLY_ON_SELECTED_DAYS, MON_WED_FRI);
				st.bindSchedule(stSch);
				schedules.save(stSch);
			}
		}

		Challenge ch0 = chList.get(0);
		User p0a = seedUsers.get(1);
		User p0b = seedUsers.get(2);
		checkIns.save(new CheckIn(p0a, ch0, ch0.getStartDate(), null));
		checkIns.save(new CheckIn(p0b, ch0, ch0.getStartDate().plusDays(1), null));

		List<SubTask> ch0subs = subtasksByChallenge.get(0);
		if (!ch0subs.isEmpty()) {
			SubTask st0 = ch0subs.getFirst();
			checkIns.save(new CheckIn(seedUsers.get(3), ch0, ch0.getStartDate().plusDays(2), st0));
		}

		Challenge ch3 = chList.get(3);
		List<SubTask> ch3subs = subtasksByChallenge.get(3);
		if (ch3subs.size() >= 2) {
			checkIns.save(
					new CheckIn(seedUsers.get(4), ch3, ch3.getStartDate().plusDays(1), ch3subs.get(1)));
		}

		Challenge ch6 = chList.get(6);
		checkIns.save(new CheckIn(seedUsers.get(8), ch6, ch6.getStartDate(), null));
		List<SubTask> ch6subs = subtasksByChallenge.get(6);
		if (!ch6subs.isEmpty()) {
			checkIns.save(new CheckIn(seedUsers.get(9), ch6, ch6.getStartDate().plusDays(3), ch6subs.getFirst()));
		}

		User u8 = seedUsers.get(8);
		User u9 = seedUsers.get(9);
		User u0 = seedUsers.getFirst();
		User u1 = seedUsers.get(1);
		User u2 = seedUsers.get(2);
		User u3 = seedUsers.get(3);
		User u4 = seedUsers.get(4);

		Invite inv1 = invites.save(new Invite(u8, u9, ch0));
		inv1.setStatus(InviteStatus.PENDING);
		invites.save(inv1);

		Invite inv2 = invites.save(new Invite(u1, u8, chList.get(5)));
		inv2.setStatus(InviteStatus.PENDING);
		invites.save(inv2);

		if (!ch3subs.isEmpty()) {
			Invite inv3 = invites.save(new Invite(u2, u9, ch3, ch3subs.getFirst()));
			inv3.setStatus(InviteStatus.PENDING);
			invites.save(inv3);
		}

		Challenge ch9 = chList.get(9);
		Invite inv4 = invites.save(new Invite(u0, u1, ch9));
		inv4.setStatus(InviteStatus.ACCEPTED);
		invites.save(inv4);
		participants.save(new Participant(u1, ch9));

		Invite inv5 = invites.save(new Invite(u3, u4, chList.get(2)));
		inv5.setStatus(InviteStatus.DECLINED);
		inv5.setExpiresAt(Instant.parse("2026-05-01T00:00:00Z"));
		invites.save(inv5);
	}
}
