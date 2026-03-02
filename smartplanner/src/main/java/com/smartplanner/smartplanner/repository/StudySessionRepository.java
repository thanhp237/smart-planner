package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StudySessionRepository extends JpaRepository<StudySession, Integer> {
    List<StudySession> findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            Integer userId, LocalDate from, LocalDate to);

    List<StudySession> findByScheduleIdOrderBySessionDateAscStartTimeAsc(Integer scheduleId);

    void deleteByScheduleId(Integer scheduleId);

    // Dashboard queries
    List<StudySession> findBySchedule_User_IdAndStatusAndSessionDateBetween(
            Integer userId, String status, LocalDate start, LocalDate end);

    @Query("SELECT s.status, COUNT(s) FROM StudySession s WHERE s.schedule.user.id = :userId GROUP BY s.status")
    List<Object[]> countByStatusGrouped(@Param("userId") Integer userId);

    @Query("SELECT DISTINCT s.sessionDate FROM StudySession s WHERE s.schedule.user.id = :userId AND s.status = 'COMPLETED' ORDER BY s.sessionDate DESC")
    List<LocalDate> findDistinctCompletedSessionDates(@Param("userId") Integer userId);
}
