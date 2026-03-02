package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.model.*;
import com.smartplanner.smartplanner.repository.CalendarAccountRepository;
import com.smartplanner.smartplanner.repository.CalendarEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarService {

    private final CalendarAccountRepository calendarAccountRepository;
    private final CalendarEventRepository calendarEventRepository;

    public CalendarService(CalendarAccountRepository calendarAccountRepository,
                           CalendarEventRepository calendarEventRepository) {
        this.calendarAccountRepository = calendarAccountRepository;
        this.calendarEventRepository = calendarEventRepository;
    }

    @Transactional
    public CalendarAccount connectAccount(User user, String provider, String externalAccountId, String accessToken,
                                          String refreshToken, LocalDateTime tokenExpiry, String scope) {
        CalendarAccount account = new CalendarAccount();
        account.setUser(user);
        account.setProvider(provider);
        account.setExternalAccountId(externalAccountId);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiry(tokenExpiry);
        account.setScope(scope);
        account.setCreatedAt(LocalDateTime.now());
        return calendarAccountRepository.save(account);
    }

    @Transactional
    public void upsertEventsForSessions(User user, List<StudySession> sessions) {
        for (StudySession session : sessions) {
            String externalId = generateDeterministicEventId(user, session);
            CalendarEvent event = calendarEventRepository.findByUserIdAndExternalEventId(user.getId(), externalId)
                    .orElseGet(() -> {
                        CalendarEvent e = new CalendarEvent();
                        e.setUser(user);
                        e.setSession(session);
                        e.setExternalEventId(externalId);
                        return e;
                    });
            event.setStatus("ACTIVE");
            event.setLastSyncAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());
            calendarEventRepository.save(event);
        }
    }

    private String generateDeterministicEventId(User user, StudySession session) {
        String raw = user.getId() + "-" + session.getId() + "-" + session.getSessionDate() + "-" + session.getStartTime();
        return UUID.nameUUIDFromBytes(raw.getBytes()).toString();
    }
}

