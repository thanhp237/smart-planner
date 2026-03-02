package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.timetable.*;
import com.smartplanner.smartplanner.model.*;
import com.smartplanner.smartplanner.repository.*;
import com.smartplanner.smartplanner.util.WeekUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private final StudyPreferenceRepository prefRepo;
    private final AvailabilitySlotRepository availRepo;
    private final CourseRepository courseRepo;
    private final TaskRepository taskRepo;
    private final StudyScheduleRepository scheduleRepo;
    private final StudySessionRepository sessionRepo;
    private final ScheduleEvaluationRepository evalRepo;
    private final UserRepository userRepo;
    private final jakarta.persistence.EntityManager entityManager;
    private final CalendarService calendarService;
    private final ReminderService reminderService;

    public TimetableService(StudyPreferenceRepository prefRepo,
            AvailabilitySlotRepository availRepo,
            CourseRepository courseRepo,
            TaskRepository taskRepo,
            StudyScheduleRepository scheduleRepo,
            StudySessionRepository sessionRepo,
            ScheduleEvaluationRepository evalRepo,
            UserRepository userRepo,
            jakarta.persistence.EntityManager entityManager,
            CalendarService calendarService,
            ReminderService reminderService) {
        this.prefRepo = prefRepo;
        this.availRepo = availRepo;
        this.courseRepo = courseRepo;
        this.taskRepo = taskRepo;
        this.scheduleRepo = scheduleRepo;
        this.sessionRepo = sessionRepo;
        this.evalRepo = evalRepo;
        this.userRepo = userRepo;
        this.entityManager = entityManager;
        this.calendarService = calendarService;
        this.reminderService = reminderService;
    }

    @Transactional
    public TimetableResponse getTimetable(Integer userId, int weekOffset) {
        LocalDate weekStart = WeekUtil.weekStart(LocalDate.now(), weekOffset);
        LocalDate weekEnd = WeekUtil.weekEnd(weekStart);

        Optional<StudySchedule> optSchedule = scheduleRepo.findByUserIdAndWeekStartDate(userId, weekStart);
        if (optSchedule.isEmpty()) {
            return new TimetableResponse(weekStart, weekEnd, null, null, List.of());
        }

        StudySchedule schedule = optSchedule.get();
        // Auto-skip past sessions
        markMissedSessionsAsSkipped(schedule.getId());

        List<StudySession> sessions = sessionRepo.findByScheduleIdOrderBySessionDateAscStartTimeAsc(schedule.getId());
        ScheduleEvaluation eval = evalRepo.findByScheduleId(schedule.getId()).orElse(null);

        return new TimetableResponse(
                weekStart,
                weekEnd,
                toScheduleResponse(schedule),
                eval == null ? null
                        : new ScheduleEvaluationResponse(eval.getScore(), eval.getLevel(), eval.getFeedback()),
                sessions.stream().map(TimetableService::toSessionResponse).toList());
    }

    @Transactional
    public void markMissedSessionsAsSkipped(Integer scheduleId) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        
        sessionRepo.updateStatusToSkippedIfMissed(scheduleId, today, now);
    }

    @Transactional
    public void generateAll(Integer userId, int blockMinutes) {
        // 1. Get Plan End Date
        StudyPreference pref = prefRepo.findByUserId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please save settings first"));
        LocalDate planEnd = pref.getPlanEndDate();
        if (planEnd == null) {
            // Default to 4 weeks if not set
            planEnd = LocalDate.now().plusWeeks(4);
        }

        LocalDate currentWeekStart = WeekUtil.weekStart(LocalDate.now(), 0);
        LocalDate endWeekStart = WeekUtil.weekStart(planEnd, 0);

        // Limit max weeks to avoid infinite loop or heavy load (e.g. max 12 weeks)
        if (ChronoUnit.WEEKS.between(currentWeekStart, endWeekStart) > 12) {
             endWeekStart = currentWeekStart.plusWeeks(12);
        }

        // 2. Loop week by week
        LocalDate cursor = currentWeekStart;
        while (!cursor.isAfter(endWeekStart)) {
            // Calculate offset relative to NOW
            int offset = (int) ChronoUnit.WEEKS.between(currentWeekStart, cursor);
            // Call generate for this week
            generate(userId, offset, blockMinutes);
            
            cursor = cursor.plusWeeks(1);
        }
    }

    @Transactional
    public TimetableResponse generate(Integer userId, int weekOffset, int blockMinutes) {
        if (blockMinutes != 30 && blockMinutes != 60 && blockMinutes != 90 && blockMinutes != 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blockMinutes must be one of 30,60,90,120");
        }

        LocalDate weekStart = WeekUtil.weekStart(LocalDate.now(), weekOffset);
        LocalDate weekEnd = WeekUtil.weekEnd(weekStart);

        // 1) Load preference (auto default if missing)
        StudyPreference pref = prefRepo.findByUserId(userId).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            StudyPreference p = new StudyPreference();
            p.setUser(user);
            p.setAllowedDays("MON,TUE,WED,THU,FRI");
            p.setMaxHoursPerDay(new BigDecimal("2.00"));
            p.setBlockMinutes(60);
            p.setCreatedAt(LocalDateTime.now());
            return prefRepo.save(p);
        });

        Set<DayOfWeek> allowedDays = parseAllowedDays(pref.getAllowedDays());
        int maxMinutesPerDay = pref.getMaxHoursPerDay()
                .multiply(BigDecimal.valueOf(60))
                .intValue();

        // 2) Load availability (active)
        List<AvailabilitySlot> avail = availRepo.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId)
                .stream().filter(a -> Boolean.TRUE.equals(a.getActive()))
                .toList();

        // 3) Load tasks (OPEN). Ignore tasks whose course is deleted or archived.
        List<Task> tasks = taskRepo.findByUserIdOrderByDeadlineDateAsc(userId).stream()
                .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()))
                .filter(t -> {
                    Course c = t.getCourse();
                    if (c == null) return true; // allow tasks without subject
                    if (Boolean.TRUE.equals(c.getIsDeleted())) return false;
                    String st = c.getStatus() == null ? "ACTIVE" : c.getStatus();
                    return "ACTIVE".equalsIgnoreCase(st);
                })
                .toList();

        // If no availability or no tasks => return clean empty with warning
        List<String> warnings = new ArrayList<>();
        if (avail.isEmpty())
            warnings.add("NO_AVAILABILITY");
        if (tasks.isEmpty())
            warnings.add("NO_TASKS");

        // 4) Upsert schedule row
        StudySchedule schedule = scheduleRepo.findByUserIdAndWeekStartDate(userId, weekStart).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            StudySchedule s = new StudySchedule();
            s.setUser(user);
            s.setWeekStartDate(weekStart);
            return s;
        });
        schedule.setGeneratedAt(LocalDateTime.now());

        schedule = scheduleRepo.save(schedule);

        // Clear old sessions & evaluation (regenerate) but KEEP completed ones
        // sessionRepo.deleteByScheduleId(schedule.getId());
        sessionRepo.deleteByScheduleIdAndStatusNot(schedule.getId(), "COMPLETED");
        evalRepo.deleteByScheduleId(schedule.getId());

        // Flush to synchronize persistence context and clear deleted entities
        entityManager.flush();
        entityManager.clear();

        // Reload schedule to get a fresh managed entity without references to deleted
        // evaluation
        schedule = scheduleRepo.findById(schedule.getId()).orElseThrow();
        
        // Fetch existing completed sessions to avoid overlap
        List<StudySession> existingSessions = sessionRepo.findByScheduleIdAndStatus(schedule.getId(), "COMPLETED");

        if (!warnings.isEmpty()) {
            schedule.setWarnings(String.join(",", warnings));
            schedule.setConfidenceScore(0);
            scheduleRepo.save(schedule);

            // Create evaluation with proper schedule relationship
            StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
            ScheduleEvaluation eval = new ScheduleEvaluation();
            eval.setSchedule(scheduleEntity);
            eval.setScore(0);
            eval.setLevel("LOW");
            eval.setFeedback("Please add availability slots and tasks before generating a timetable.");
            eval.setCreatedAt(LocalDateTime.now());
            evalRepo.save(eval);

            return getTimetable(userId, weekOffset);
        }

        // 5) Build blocks (supply) within the week
        // Pass today and plan dates to filter out invalid days
        List<Block> blocks = buildBlocks(weekStart, allowedDays, avail, blockMinutes, maxMinutesPerDay,
                LocalDate.now(), pref.getPlanStartDate(), pref.getPlanEndDate(), existingSessions);
        
        if (blocks.isEmpty()) {
            warnings.add("NO_BLOCKS_AVAILABLE_AFTER_TODAY");
            schedule.setWarnings(String.join(",", warnings));
            schedule.setConfidenceScore(0);
            scheduleRepo.save(schedule);
            
            // Create evaluation with explicit feedback
            StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
            ScheduleEvaluation eval = new ScheduleEvaluation();
            eval.setSchedule(scheduleEntity);
            eval.setScore(0);
            eval.setLevel("LOW");
            eval.setFeedback("No available study blocks found for the remaining days of this week. Check your 'Study Settings' (Plan End Date, Allowed Days) and 'Availability'.");
            eval.setCreatedAt(LocalDateTime.now());
            evalRepo.save(eval);
            
            return getTimetable(userId, weekOffset);
        }

        int capacityMinutes = blocks.stream().mapToInt(b -> b.minutes).sum();

        // 6) Demand (minutes)
        int demandMinutes = tasks.stream()
                .mapToInt(t -> hoursToMinutes(t.getRemainingHours()))
                .sum();

        if (demandMinutes > capacityMinutes)
            warnings.add("OVER_CAPACITY");

        // 7) Allocate (Greedy + Weighted RR)
        // Greedy order: deadline asc, then priority weight desc
        List<TaskState> states = tasks.stream()
                .sorted(Comparator
                        .comparing(Task::getDeadlineDate)
                        .thenComparing((Task t) -> priorityWeight(t.getPriority()), Comparator.reverseOrder()))
                .map(TaskState::new)
                .toList();

        WeightedRoundRobin rr = new WeightedRoundRobin(states);

        Map<LocalDate, Integer> usedMinutesByDay = new HashMap<>();
        Map<Integer, Integer> allocatedByTask = new HashMap<>();
        List<StudySession> newSessions = new ArrayList<>();

        for (Block b : blocks) {
            TaskState chosen = rr.nextEligible(b.date);
            if (chosen == null)
                break; // no eligible tasks left

            int remaining = chosen.remainingMinutes;
            if (remaining <= 0)
                continue;

            // allocate min(block, remaining)
            int alloc = Math.min(b.minutes, remaining);

            // make sure not exceed maxMinutesPerDay
            int usedToday = usedMinutesByDay.getOrDefault(b.date, 0);
            if (usedToday + alloc > maxMinutesPerDay) {
                continue; // skip this block (day cap reached)
            }

            // If task deadline before session date => skip (should already be filtered in
            // rr)
            if (chosen.deadline.isBefore(b.date))
                continue;

            // Load entities for relationships
            StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
            Course courseEntity = null;
            if (chosen.courseId != null) {
                courseEntity = courseRepo.findById(chosen.courseId).orElseThrow();
            }
            Task taskEntity = taskRepo.findById(chosen.taskId).orElseThrow();

            // Create session
            StudySession s = new StudySession();
            s.setSchedule(scheduleEntity);
            s.setCourse(courseEntity);
            s.setTask(taskEntity);
            s.setSessionDate(b.date);
            s.setStartTime(b.start);
            s.setEndTime(b.start.plusMinutes(alloc));
            s.setDurationMinutes(alloc);
            s.setStatus("PLANNED");
            s.setCreatedAt(LocalDateTime.now());

            newSessions.add(s);

            // Update tracking
            chosen.remainingMinutes -= alloc;
            usedMinutesByDay.put(b.date, usedToday + alloc);
            allocatedByTask.put(chosen.taskId, allocatedByTask.getOrDefault(chosen.taskId, 0) + alloc);
        }

        // deadline risk warning: if deadline within this week but not enough allocated
        // to cover remaining
        for (TaskState st : states) {
            if (!st.deadline.isBefore(weekStart) && !st.deadline.isAfter(weekEnd)) {
                int alloc = allocatedByTask.getOrDefault(st.taskId, 0);
                if (alloc < st.originalRemainingMinutes) {
                    warnings.add("DEADLINE_RISK");
                    break;
                }
            }
        }

        sessionRepo.saveAll(newSessions);

        User scheduleUser = schedule.getUser();
        calendarService.upsertEventsForSessions(scheduleUser, newSessions);
        reminderService.createDefaultRemindersForSessions(newSessions);

        // 8) Score + feedback
        ScoreResult scoreResult = scoreSchedule(maxMinutesPerDay, usedMinutesByDay, capacityMinutes, demandMinutes,
                newSessions.size());
        int overCapacityMinutes = Math.max(0, demandMinutes - capacityMinutes);
        ScoreResult base = scoreSchedule(maxMinutesPerDay, usedMinutesByDay, capacityMinutes, demandMinutes,
                newSessions.size());

        int score = base.score();
        String level = base.level();
        String feedback = base.feedback();

        if (overCapacityMinutes > 0) {
            // phạt theo mức thiếu: thiếu mỗi 60 phút trừ 5 điểm + trừ base 10
            int penalty = 10 + (overCapacityMinutes / 60) * 5;
            penalty = Math.min(35, penalty); // cap tối đa 35

            score = Math.max(0, score - penalty);
            level = score >= 80 ? "HIGH" : (score >= 50 ? "MEDIUM" : "LOW");

            int missingHours = (int) Math.ceil(overCapacityMinutes / 60.0);
            feedback = feedback + " Over capacity: you are missing about " + missingHours
                    + " hour(s) of available time this week. Add availability or reduce task hours.";
            
            // Check for future days with NO blocks
            Set<LocalDate> daysWithBlocks = blocks.stream().map(Block::date).collect(Collectors.toSet());
            List<String> missingDayNames = new ArrayList<>();
            LocalDate today = LocalDate.now();
            LocalDate check = today.plusDays(1); // Start from tomorrow
            while (!check.isAfter(weekEnd)) {
                if (!daysWithBlocks.contains(check)) {
                    missingDayNames.add(check.getDayOfWeek().name());
                }
                check = check.plusDays(1);
            }
            
            if (!missingDayNames.isEmpty()) {
                feedback += " Warning: No availability found for " + String.join(", ", missingDayNames) + ". Please add slots.";
            }
        }
        schedule.setConfidenceScore(score);
        schedule.setWarnings(warnings.isEmpty() ? null : String.join(",", distinct(warnings)));
        scheduleRepo.save(schedule);

        // Create new evaluation (we deleted the old one at line 130)
        StudySchedule scheduleEntity = scheduleRepo.findById(schedule.getId()).orElseThrow();
        ScheduleEvaluation eval = new ScheduleEvaluation();
        eval.setSchedule(scheduleEntity);
        eval.setScore(score);
        eval.setLevel(level);
        eval.setFeedback(feedback);
        eval.setCreatedAt(LocalDateTime.now());

        evalRepo.save(eval);

        return getTimetable(userId, weekOffset);
    }

    // ----------------- helpers -----------------

    private static StudyScheduleResponse toScheduleResponse(StudySchedule s) {
        return new StudyScheduleResponse(s.getId(), s.getWeekStartDate(), s.getGeneratedAt(), s.getConfidenceScore(),
                s.getWarnings());
    }

    private static StudySessionResponse toSessionResponse(StudySession s) {
        Integer courseId = s.getCourse() != null ? s.getCourse().getId() : null;
        String courseName;
        if (s.getCourse() == null) {
            courseName = "No subject assigned";
        } else if (Boolean.TRUE.equals(s.getCourse().getIsDeleted())) {
            courseName = "Deleted subject";
        } else {
            courseName = s.getCourse().getName();
        }
        return new StudySessionResponse(
                s.getId(),
                courseId,
                s.getTask() != null ? s.getTask().getId() : null,
                s.getSessionDate(),
                s.getStartTime(),
                s.getEndTime(),
                s.getDurationMinutes(),
                s.getStatus(),
                courseName,
                s.getTask() != null ? s.getTask().getTitle() : "Tự học");
    }

    private static int hoursToMinutes(BigDecimal hours) {
        if (hours == null)
            return 0;
        return hours.multiply(BigDecimal.valueOf(60)).intValue();
    }

    private static int priorityWeight(String p) {
        if (p == null)
            return 2;
        return switch (p.toUpperCase()) {
            case "HIGH" -> 3;
            case "LOW" -> 1;
            default -> 2; // MEDIUM
        };
    }

    private static Set<DayOfWeek> parseAllowedDays(String allowedDays) {
        if (allowedDays == null || allowedDays.isBlank())
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY);

        Set<DayOfWeek> set = new HashSet<>();
        for (String token : allowedDays.split(",")) {
            String t = token.trim().toUpperCase();
            switch (t) {
                case "MON" -> set.add(DayOfWeek.MONDAY);
                case "TUE" -> set.add(DayOfWeek.TUESDAY);
                case "WED" -> set.add(DayOfWeek.WEDNESDAY);
                case "THU" -> set.add(DayOfWeek.THURSDAY);
                case "FRI" -> set.add(DayOfWeek.FRIDAY);
                case "SAT" -> set.add(DayOfWeek.SATURDAY);
                case "SUN" -> set.add(DayOfWeek.SUNDAY);
            }
        }
        return set;
    }

    private static int minutesOfDay(LocalTime t) {
        if (t == null) return 0;
        return t.getHour() * 60 + t.getMinute();
    }

    private static List<Block> buildBlocks(LocalDate weekStart,
            Set<DayOfWeek> allowedDays,
            List<AvailabilitySlot> avail,
            int blockMinutes,
            int maxMinutesPerDay,
            LocalDate today,
            LocalDate planStartDate,
            LocalDate planEndDate,
            List<StudySession> existingSessions) {

        // group availability by dayOfWeek (1..7)
        Map<Integer, List<AvailabilitySlot>> byDow = avail.stream()
                .collect(Collectors.groupingBy(a -> (int) a.getDayOfWeek()));
        
        // Group existing sessions by date to speed up overlap checks
        Map<LocalDate, List<StudySession>> sessionsByDate = existingSessions.stream()
                .collect(Collectors.groupingBy(StudySession::getSessionDate));

        List<Block> blocks = new ArrayList<>();
        int breakMinutes = Math.max(1, (int) Math.round(blockMinutes * 0.2));

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);

            // 1. Skip past days (MODIFIED: User wants to see past slots as SKIPPED, so we DO NOT skip them)
            // if (date.isBefore(today)) {
            //    continue;
            // }

            // 2. Skip if outside plan range (if set)
            if (planStartDate != null && date.isBefore(planStartDate)) {
                continue;
            }
            if (planEndDate != null && date.isAfter(planEndDate)) {
                continue;
            }

            DayOfWeek dow = date.getDayOfWeek();
            if (!allowedDays.contains(dow))
                continue;

            int dowNum = dow.getValue(); // Mon=1..Sun=7
            List<AvailabilitySlot> slots = byDow.getOrDefault(dowNum, List.of());
            if (slots.isEmpty())
                continue;
            
            // Get existing sessions for this day
            List<StudySession> daySessions = sessionsByDate.getOrDefault(date, List.of());

            int used = 0;
            // Adjust 'used' by existing sessions duration if we want to enforce daily max including completed sessions?
            // Currently maxMinutesPerDay is per generated schedule.
            // If user did 1 hour already, we should probably count it towards the daily max.
            int alreadyUsed = daySessions.stream().mapToInt(StudySession::getDurationMinutes).sum();
            used += alreadyUsed;

            Integer lastBlockEndMins = null;
            for (AvailabilitySlot s : slots) {
                int startMins = minutesOfDay(s.getStartTime());
                int endMins = minutesOfDay(s.getEndTime());
                if (endMins <= startMins) {
                    continue;
                }

                int curMins = startMins;
                if (lastBlockEndMins != null) {
                    curMins = Math.max(curMins, lastBlockEndMins + breakMinutes);
                }
                while (curMins + blockMinutes <= endMins && curMins + blockMinutes <= 1440) {
                    if (used + blockMinutes > maxMinutesPerDay)
                        break;
                    
                    // Check overlap with existing sessions
                    int blockStartMins = curMins;
                    int blockEndMins = curMins + blockMinutes;
                    boolean overlaps = false;
                    for (StudySession sess : daySessions) {
                        int sessStartMins = minutesOfDay(sess.getStartTime());
                        int dur = sess.getDurationMinutes() == null ? 0 : sess.getDurationMinutes();
                        int sessEndMins = dur > 0 ? sessStartMins + dur : minutesOfDay(sess.getEndTime());
                        if (sessEndMins <= sessStartMins) {
                            sessEndMins = Math.min(1440, sessStartMins + Math.max(1, dur));
                        } else {
                            sessEndMins = Math.min(1440, sessEndMins);
                        }

                        if (blockStartMins < sessEndMins && sessStartMins < blockEndMins) {
                            overlaps = true;
                            break;
                        }
                    }
                    
                    if (!overlaps) {
                        LocalTime blockStart = LocalTime.ofSecondOfDay(blockStartMins * 60L);
                        blocks.add(new Block(date, blockStart, blockMinutes));
                        used += blockMinutes;
                        lastBlockEndMins = blockEndMins;
                        curMins = blockEndMins + breakMinutes;
                        continue;
                    }
                    
                    curMins += blockMinutes;
                }
            }
        }

        // Already in chronological order by construction (Mon..Sun, startTime)
        // REBALANCE: Sort blocks in a round-robin day fashion to avoid front-loading (e.g. Mon, Tue, Wed... instead of Mon, Mon, Tue, Tue...)
        // This helps distribute workload evenly across the week.
        
        // 1. Group by date index (0..6)
        Map<Integer, List<Block>> blocksByDayIndex = new HashMap<>();
        for (Block b : blocks) {
            int dayIndex = (int) ChronoUnit.DAYS.between(weekStart, b.date);
            blocksByDayIndex.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(b);
        }

        // 2. Interleave blocks
        List<Block> balancedBlocks = new ArrayList<>();
        // Use a more robust interleaving that handles varying list sizes better
        // Iterate until no blocks left in any day list
        int maxLen = 0;
        for (List<Block> list : blocksByDayIndex.values()) {
            maxLen = Math.max(maxLen, list.size());
        }
        
        for (int i = 0; i < maxLen; i++) {
            for (int d = 0; d < 7; d++) {
                List<Block> dayBlocks = blocksByDayIndex.get(d);
                if (dayBlocks != null && i < dayBlocks.size()) {
                    balancedBlocks.add(dayBlocks.get(i));
                }
            }
        }

        return balancedBlocks;
    }

    private static ScoreResult scoreSchedule(int maxMinutesPerDay,
            Map<LocalDate, Integer> usedMinutesByDay,
            int capacityMinutes,
            int demandMinutes,
            int sessionCount) {
        int score = 100;
        List<String> feedbacks = new ArrayList<>();

        // 1) Under-allocation (if there is demand)
        int allocated = usedMinutesByDay.values().stream().mapToInt(i -> i).sum();
        if (demandMinutes > 0 && allocated < Math.min(demandMinutes, capacityMinutes)) {
            int gap = Math.min(demandMinutes, capacityMinutes) - allocated;
            int penalty = Math.min(40, gap / 30); // up to 40
            score -= penalty;
            feedbacks.add("You did not allocate enough study time for your tasks in this week.");
        }

        // 2) Too many heavy days (>= 90% of max/day)
        long heavyDays = usedMinutesByDay.values().stream().filter(m -> m >= (int) (0.9 * maxMinutesPerDay)).count();
        if (heavyDays >= 3) {
            score -= 15;
            feedbacks.add("Several days are packed near your daily limit. Consider spreading sessions more evenly.");
        }

        // 3) Imbalance between days (simple range check)
        int min = usedMinutesByDay.values().stream().min(Integer::compareTo).orElse(0);
        int max = usedMinutesByDay.values().stream().max(Integer::compareTo).orElse(0);
        if (max - min >= 120) { // >= 2 hours difference
            score -= 15;
            feedbacks.add("Daily workload is imbalanced (some days much heavier). Try balancing across the week.");
        }

        // 4) Too few sessions (often means schedule is sparse)
        if (sessionCount <= 2 && demandMinutes > 0) {
            score -= 10;
            feedbacks.add("Your schedule has very few sessions. Add more availability or reduce block size.");
        }

        score = Math.max(0, Math.min(100, score));

        String level = score >= 80 ? "HIGH" : (score >= 50 ? "MEDIUM" : "LOW");

        if (feedbacks.isEmpty()) {
            feedbacks.add("Good schedule. Keep a consistent pace and review upcoming deadlines.");
        }

        return new ScoreResult(score, level, String.join(" ", feedbacks));
    }

    private static List<String> distinct(List<String> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    private record Block(LocalDate date, LocalTime start, int minutes) {
    }

    private static class TaskState {
        final int taskId;
        final Integer courseId;
        final LocalDate deadline;
        final int weight;
        int remainingMinutes;
        final int originalRemainingMinutes;

        TaskState(Task t) {
            this.taskId = t.getId();
            this.courseId = t.getCourse() != null ? t.getCourse().getId() : null;
            this.deadline = t.getDeadlineDate();
            this.weight = priorityWeight(t.getPriority());
            this.remainingMinutes = hoursToMinutes(t.getRemainingHours());
            this.originalRemainingMinutes = this.remainingMinutes;
        }
    }

    private static class WeightedRoundRobin {
        private final List<TaskState> states;
        private int idx = 0;
        private int weightCursor = 0;

        WeightedRoundRobin(List<TaskState> states) {
            this.states = states;
        }

        TaskState nextEligible(LocalDate sessionDate) {
            if (states.isEmpty())
                return null;

            // Try at most N*4 steps to find eligible
            int tries = states.size() * 4;
            while (tries-- > 0) {
                TaskState st = states.get(idx);

                boolean eligible = st.remainingMinutes > 0 && !st.deadline.isBefore(sessionDate);
                if (eligible) {
                    // weighted: same task repeated weight times before moving on
                    if (weightCursor < st.weight - 1) {
                        weightCursor++;
                    } else {
                        weightCursor = 0;
                        idx = (idx + 1) % states.size();
                    }
                    return st;
                }

                // move next
                weightCursor = 0;
                idx = (idx + 1) % states.size();
            }
            return null;
        }
    }

    private record ScoreResult(int score, String level, String feedback) {
    }
}
