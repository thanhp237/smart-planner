package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.calendar.ConnectCalendarRequest;
import com.smartplanner.smartplanner.model.CalendarAccount;
import com.smartplanner.smartplanner.model.StudySchedule;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.CalendarAccountRepository;
import com.smartplanner.smartplanner.repository.StudyScheduleRepository;
import com.smartplanner.smartplanner.repository.StudySessionRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import com.smartplanner.smartplanner.service.CalendarService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final CalendarAccountRepository calendarAccountRepository;
    private final UserRepository userRepository;
    private final StudyScheduleRepository scheduleRepository;
    private final StudySessionRepository sessionRepository;

    public CalendarController(CalendarService calendarService,
                              CalendarAccountRepository calendarAccountRepository,
                              UserRepository userRepository,
                              StudyScheduleRepository scheduleRepository,
                              StudySessionRepository sessionRepository) {
        this.calendarService = calendarService;
        this.calendarAccountRepository = calendarAccountRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/connect")
    public ResponseEntity<CalendarAccount> connect(@RequestBody ConnectCalendarRequest request,
                                                   HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        User user = userRepository.findById(userId).orElseThrow();

        String provider = request.provider() != null ? request.provider() : "GOOGLE";

        CalendarAccount account = calendarService.connectAccount(
                user,
                provider,
                request.externalAccountId(),
                request.accessToken(),
                request.refreshToken(),
                request.tokenExpiry(),
                request.scope());

        return ResponseEntity.ok(account);
    }

    @PostMapping("/sync-week")
    public ResponseEntity<String> syncCurrentWeek(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        LocalDate today = LocalDate.now();

        LocalDate weekStart = com.smartplanner.smartplanner.util.WeekUtil.weekStart(today, 0);
        StudySchedule schedule = scheduleRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .orElse(null);
        if (schedule == null) {
            return ResponseEntity.ok("No schedule for this week");
        }

        List<StudySession> sessions = sessionRepository
                .findByScheduleIdOrderBySessionDateAscStartTimeAsc(schedule.getId());

        User user = schedule.getUser();
        calendarService.upsertEventsForSessions(user, sessions);

        return ResponseEntity.ok("Synced " + sessions.size() + " sessions to calendar events");
    }
}

