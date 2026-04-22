package com.challenges.api.dev;

import com.challenges.api.model.Challenge;
import com.challenges.api.model.ChallengeCategory;
import com.challenges.api.model.CheckIn;
import com.challenges.api.model.Comment;
import com.challenges.api.model.Invite;
import com.challenges.api.model.InviteStatus;
import com.challenges.api.model.Participant;
import com.challenges.api.model.Schedule;
import com.challenges.api.model.ScheduleKind;
import com.challenges.api.model.SubTask;
import com.challenges.api.model.User;
import com.challenges.api.repo.ChallengeRepository;
import com.challenges.api.repo.CheckInRepository;
import com.challenges.api.repo.CommentRepository;
import com.challenges.api.repo.InviteRepository;
import com.challenges.api.repo.ParticipantRepository;
import com.challenges.api.repo.ScheduleRepository;
import com.challenges.api.repo.SubTaskRepository;
import com.challenges.api.repo.UserRepository;
import com.challenges.api.support.ChallengeLocationMapping;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.datafaker.Faker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo-seed")
public class DemoDataSeedService {

	static final int BULK_USER_COUNT = 1_000;
	static final int BULK_CHALLENGE_COUNT = 1_000;
	/**
	 * Challenges whose zero-based index is divisible by this value are seeded as private (e.g. {@code 200}
	 * → indices 0, 200, 400, … are private).
	 */
	static final int PRIVATE_CHALLENGE_INDEX_MOD = 200;
	/**
	 * Non-owner participants per challenge (the owner also gets a challenge-wide {@link Participant} row, like
	 * {@code POST /api/challenges}).
	 */
	static final int PARTICIPANTS_PER_CHALLENGE = 10;
	/** Total participant rows per challenge ({@link #PARTICIPANTS_PER_CHALLENGE} + owner). */
	static final int PARTICIPANT_ROWS_PER_CHALLENGE = PARTICIPANTS_PER_CHALLENGE + 1;
	/** Check-ins per seeded participant (split: half challenge-wide, half on a subtask). */
	static final int CHECKINS_PER_PARTICIPANT = 4;
	/**
	 * Every {@code N}th challenge gets invites, comments, and one subtask-scoped-only participant so demo data
	 * exercises invite lists, join-related visibility, comments, and subtask-only membership.
	 */
	static final int ENRICH_EVERY_N_CHALLENGES = 100;
	/** Invites added per enriched challenge (pending/accepted/declined/cancelled + subtask pending). */
	static final int INVITES_PER_ENRICHED_CHALLENGE = 5;
	/** Challenge-wide + subtask comments per enriched challenge. */
	static final int COMMENTS_PER_ENRICHED_CHALLENGE = 2;
	/**
	 * Challenges whose index is divisible by this value have no {@code city} or {@code location} (remainder get a
	 * rotating demo place).
	 */
	static final int NO_LOCATION_CHALLENGE_INDEX_MOD = 4;

	/**
	 * First seeded user email — idempotency guard ({@link #seedIfEmpty()} skips if this exists).
	 */
	static final String SEED_EMAIL_1 = "bulk-demo-0000@demo.local";

	private static final int TITLE_MAX = 500;
	private static final int DESCRIPTION_MAX = 8000;
	private static final int COMMENT_BODY_MAX = 8000;

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

	/** Representative WGS-84 points for demo challenges (rotated by challenge index). */
	private static final List<DemoCity> SEED_CITIES = List.of(
			new DemoCity("Berlin", 52.520008, 13.404954),
			new DemoCity("Brandenburg an der Havel", 52.4125, 12.5311),
			new DemoCity("Hamburg", 53.551086, 9.993682),
			new DemoCity("Dresden", 51.050407, 13.737262),
			new DemoCity("Munich", 48.137154, 11.576124));

	private final UserRepository users;
	private final ChallengeRepository challenges;
	private final SubTaskRepository subTasks;
	private final ScheduleRepository schedules;
	private final ParticipantRepository participants;
	private final CheckInRepository checkIns;
	private final InviteRepository invites;
	private final CommentRepository comments;

	public DemoDataSeedService(
			UserRepository users,
			ChallengeRepository challenges,
			SubTaskRepository subTasks,
			ScheduleRepository schedules,
			ParticipantRepository participants,
			CheckInRepository checkIns,
			InviteRepository invites,
			CommentRepository comments) {
		this.users = users;
		this.challenges = challenges;
		this.subTasks = subTasks;
		this.schedules = schedules;
		this.participants = participants;
		this.checkIns = checkIns;
		this.invites = invites;
		this.comments = comments;
	}

	@Transactional
	public void seedIfEmpty() {
		if (users.existsByEmail(SEED_EMAIL_1)) {
			return;
		}

		Faker faker = new Faker(Locale.ENGLISH);

		List<User> seedUsers = new ArrayList<>(BULK_USER_COUNT);
		for (int i = 0; i < BULK_USER_COUNT; i++) {
			String email = String.format("bulk-demo-%04d@demo.local", i);
			seedUsers.add(new User(
					faker.name().fullName(), email, User.TEST_PASSWORD_HASH, User.DEFAULT_ROLE));
		}
		users.saveAll(seedUsers);
		users.flush();

		LocalDate base = LocalDate.of(2026, 4, 1);
		ChallengeCategory[] categories = ChallengeCategory.values();
		List<Challenge> chList = new ArrayList<>(BULK_CHALLENGE_COUNT);
		for (int i = 0; i < BULK_CHALLENGE_COUNT; i++) {
			User owner = seedUsers.get(i);
			LocalDate start = base.plusDays(i % 400L);
			LocalDate end = i % 3 == 0 ? start.plusMonths(1 + (i % 3)) : null;
			String title = truncate(faker.book().title() + " — " + faker.lorem().word(), TITLE_MAX);
			String description = truncate(faker.lorem().paragraph(2 + (i % 3)), DESCRIPTION_MAX);
			boolean isPrivate = i % PRIVATE_CHALLENGE_INDEX_MOD == 0;
			Challenge ch = new Challenge(
					owner, title, description, start, end, categories[i % categories.length], null, null, isPrivate);
			if (i % NO_LOCATION_CHALLENGE_INDEX_MOD != 0) {
				DemoCity place = SEED_CITIES.get(Math.floorMod(i, SEED_CITIES.size()));
				ch.setCity(place.city);
				ch.setLocation(ChallengeLocationMapping.toPoint(place.latitude, place.longitude));
			}
			chList.add(ch);
		}
		challenges.saveAll(chList);
		challenges.flush();

		// One schedule per challenge: either DAILY or WEEKLY_ON_SELECTED_DAYS ("a few days" per week).
		for (int i = 0; i < BULK_CHALLENGE_COUNT; i++) {
			Challenge ch = chList.get(i);
			SchedulePattern p = SchedulePattern.forIndex(i);
			Schedule sch = Schedule.forChallenge(ch, p.kind, p.weekDays());
			ch.bindSchedule(sch);
			schedules.save(sch);
		}

		// 1–5 subtasks per challenge (cycles with challenge index); each subtask gets its own schedule.
		List<List<SubTask>> subtasksByChallenge = new ArrayList<>(BULK_CHALLENGE_COUNT);
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			subtasksByChallenge.add(new ArrayList<>());
		}
		List<SubTask> allSubTasks = new ArrayList<>();
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			Challenge ch = chList.get(c);
			int subtaskCount = subtaskCountForChallenge(c);
			for (int s = 0; s < subtaskCount; s++) {
				String stTitle =
						truncate(faker.lorem().sentence(2 + (s % 3)) + " (" + (s + 1) + ")", TITLE_MAX);
				SubTask st = new SubTask(ch, stTitle, s);
				allSubTasks.add(st);
				subtasksByChallenge.get(c).add(st);
			}
		}
		subTasks.saveAll(allSubTasks);
		subTasks.flush();

		int subTaskCursor = 0;
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			int subtaskCount = subtaskCountForChallenge(c);
			for (int s = 0; s < subtaskCount; s++) {
				SubTask st = allSubTasks.get(subTaskCursor++);
				SchedulePattern p = SchedulePattern.forIndex(c * 31 + s);
				Schedule stSch = Schedule.forSubTask(st, p.kind, p.weekDays());
				st.bindSchedule(stSch);
				schedules.save(stSch);
			}
		}

		// Owner as challenge-wide participant (matches create-challenge / join membership model).
		List<Participant> ownerParticipants = new ArrayList<>(BULK_CHALLENGE_COUNT);
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			Challenge ch = chList.get(c);
			ownerParticipants.add(new Participant(seedUsers.get(c), ch));
		}

		// Non-owner participants: 10 distinct users per challenge; every ENRICH_EVERY_N_CHALLENGES-th challenge
		// uses the last slot as subtask-scoped-only (API: challenge-wide check-ins require challenge-wide row).
		List<Participant> memberParticipants =
				new ArrayList<>(BULK_CHALLENGE_COUNT * PARTICIPANTS_PER_CHALLENGE);
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			Challenge ch = chList.get(c);
			List<SubTask> sts = subtasksByChallenge.get(c);
			for (int k = 0; k < PARTICIPANTS_PER_CHALLENGE; k++) {
				int uIdx = (c + 1 + k) % BULK_USER_COUNT;
				boolean subtaskOnly =
						isEnrichedChallenge(c) && k == PARTICIPANTS_PER_CHALLENGE - 1 && !sts.isEmpty();
				if (subtaskOnly) {
					memberParticipants.add(new Participant(seedUsers.get(uIdx), ch, sts.get(0)));
				} else {
					memberParticipants.add(new Participant(seedUsers.get(uIdx), ch));
				}
			}
		}
		participants.saveAll(ownerParticipants);
		participants.saveAll(memberParticipants);

		// Invites + comments on a sample of challenges (pending join, accepted/declined/cancelled, subtask invite).
		List<Invite> demoInvites = new ArrayList<>();
		List<Comment> demoComments = new ArrayList<>();
		Instant pendingExpires = Instant.now().plus(30, ChronoUnit.DAYS);
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			if (!isEnrichedChallenge(c)) {
				continue;
			}
			Challenge ch = chList.get(c);
			User owner = seedUsers.get(c);
			List<SubTask> sts = subtasksByChallenge.get(c);

			Invite pendingWide = new Invite(owner, seedUsers.get((c + 11) % BULK_USER_COUNT), ch);
			pendingWide.setExpiresAt(pendingExpires);
			demoInvites.add(pendingWide);

			if (!sts.isEmpty()) {
				Invite pendingSub = new Invite(owner, seedUsers.get((c + 12) % BULK_USER_COUNT), ch, sts.get(0));
				demoInvites.add(pendingSub);
			}

			Invite accepted = new Invite(owner, seedUsers.get((c + 1) % BULK_USER_COUNT), ch);
			accepted.setStatus(InviteStatus.ACCEPTED);
			demoInvites.add(accepted);

			Invite declined = new Invite(owner, seedUsers.get((c + 13) % BULK_USER_COUNT), ch);
			declined.setStatus(InviteStatus.DECLINED);
			demoInvites.add(declined);

			Invite cancelled = new Invite(owner, seedUsers.get((c + 14) % BULK_USER_COUNT), ch);
			cancelled.setStatus(InviteStatus.CANCELLED);
			demoInvites.add(cancelled);

			demoComments.add(
					new Comment(
							seedUsers.get((c + 1) % BULK_USER_COUNT),
							ch,
							truncate(faker.lorem().sentence(4), COMMENT_BODY_MAX)));
			if (!sts.isEmpty()) {
				demoComments.add(
						new Comment(
								owner,
								ch,
								sts.get(0),
								truncate(faker.lorem().sentence(3), COMMENT_BODY_MAX)));
			}
		}
		invites.saveAll(demoInvites);
		comments.saveAll(demoComments);

		// Check-ins: non-owner members only (owner has no seeded check-ins). Subtask-only members get only
		// subtask-scoped rows so data matches {@link com.challenges.api.service.CheckInService} rules.
		int checkInCapacity = BULK_CHALLENGE_COUNT * PARTICIPANTS_PER_CHALLENGE * CHECKINS_PER_PARTICIPANT;
		List<CheckIn> allCheckIns = new ArrayList<>(checkInCapacity);
		int challengeLevelCount = CHECKINS_PER_PARTICIPANT / 2;
		for (int c = 0; c < BULK_CHALLENGE_COUNT; c++) {
			Challenge ch = chList.get(c);
			List<SubTask> sts = subtasksByChallenge.get(c);
			for (int p = 0; p < PARTICIPANTS_PER_CHALLENGE; p++) {
				User user = seedUsers.get((c + 1 + p) % BULK_USER_COUNT);
				boolean subtaskOnlyMember =
						isEnrichedChallenge(c) && p == PARTICIPANTS_PER_CHALLENGE - 1 && !sts.isEmpty();
				for (int n = 0; n < CHECKINS_PER_PARTICIPANT; n++) {
					boolean subtaskLevel = subtaskOnlyMember || n >= challengeLevelCount;
					SubTask st =
							subtaskLevel && !sts.isEmpty() ? sts.get(p % sts.size()) : null;
					LocalDate date = checkDateForParticipantCheckIn(ch, p, n, c);
					allCheckIns.add(new CheckIn(user, ch, date, st));
				}
			}
		}
		checkIns.saveAll(allCheckIns);
	}

	private static boolean isEnrichedChallenge(int challengeIndex) {
		return challengeIndex % ENRICH_EVERY_N_CHALLENGES == 0;
	}

	private static LocalDate checkDateForParticipantCheckIn(
			Challenge ch, int participantIndex, int checkInIndex, int challengeIndex) {
		LocalDate start = ch.getStartDate();
		LocalDate end = ch.getEndDate() != null ? ch.getEndDate() : start.plusDays(90);
		if (end.isBefore(start)) {
			end = start;
		}
		long span = ChronoUnit.DAYS.between(start, end) + 1;
		long spread = Math.max(1L, span / (CHECKINS_PER_PARTICIPANT + 2));
		long dayOffset =
				1
						+ checkInIndex * spread
						+ (participantIndex % 5L)
						+ (challengeIndex % 3L);
		if (dayOffset >= span) {
			dayOffset = Math.max(0, span - 1 - checkInIndex);
		}
		return start.plusDays(dayOffset);
	}

	private static String truncate(String s, int maxLen) {
		if (s == null || s.length() <= maxLen) {
			return s == null ? "" : s;
		}
		return s.substring(0, maxLen);
	}

	private static int subtaskCountForChallenge(int challengeIndex) {
		return 1 + (challengeIndex % 5);
	}

	/**
	 * Demo schedules: every row is either {@link ScheduleKind#DAILY} (no weekday list) or
	 * {@link ScheduleKind#WEEKLY_ON_SELECTED_DAYS} with a short list of weekdays.
	 */
	private enum SchedulePattern {
		DAILY_EMPTY(ScheduleKind.DAILY),
		WEEKLY_MON_WED_FRI(ScheduleKind.WEEKLY_ON_SELECTED_DAYS),
		WEEKLY_TUE_THU(ScheduleKind.WEEKLY_ON_SELECTED_DAYS),
		WEEKLY_WEEKDAYS(ScheduleKind.WEEKLY_ON_SELECTED_DAYS),
		WEEKLY_WEEKEND(ScheduleKind.WEEKLY_ON_SELECTED_DAYS);

		final ScheduleKind kind;

		SchedulePattern(ScheduleKind kind) {
			this.kind = kind;
		}

		List<DayOfWeek> weekDays() {
			return switch (this) {
				case DAILY_EMPTY -> List.of();
				case WEEKLY_MON_WED_FRI -> MON_WED_FRI;
				case WEEKLY_TUE_THU -> TUE_THU;
				case WEEKLY_WEEKDAYS -> WEEKDAYS;
				case WEEKLY_WEEKEND -> SAT_SUN;
			};
		}

		static SchedulePattern forIndex(int i) {
			return values()[Math.floorMod(i, values().length)];
		}
	}

	private record DemoCity(String city, double latitude, double longitude) {}
}
