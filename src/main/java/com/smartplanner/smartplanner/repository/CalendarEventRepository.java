package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Integer> {

    List<CalendarEvent> findByUserId(Integer userId);

    Optional<CalendarEvent> findByUserIdAndExternalEventId(Integer userId, String externalEventId);
}

