package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.model.SessionReminder;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.repository.SessionReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {

    private final SessionReminderRepository sessionReminderRepository;

    public ReminderService(SessionReminderRepository sessionReminderRepository) {
        this.sessionReminderRepository = sessionReminderRepository;
    }

    @Transactional
    public void createDefaultRemindersForSessions(List<StudySession> sessions) {
        for (StudySession session : sessions) {
            LocalDateTime reminderTime = LocalDateTime.of(session.getSessionDate(), session.getStartTime())
                    .minusMinutes(30);

            SessionReminder reminder = new SessionReminder();
            reminder.setSession(session);
            reminder.setReminderTime(reminderTime);
            reminder.setChannel("EMAIL");
            reminder.setStatus("PENDING");
            reminder.setCreatedAt(LocalDateTime.now());

            sessionReminderRepository.save(reminder);
        }
    }
}

