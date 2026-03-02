package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.SessionReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionReminderRepository extends JpaRepository<SessionReminder, Integer> {

    List<SessionReminder> findBySessionId(Integer sessionId);

    List<SessionReminder> findByStatusAndReminderTimeBefore(String status, LocalDateTime before);
}

