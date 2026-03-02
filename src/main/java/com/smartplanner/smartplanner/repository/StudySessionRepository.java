package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.StudySession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalTime;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StudySession s WHERE s.id = :id")
    Optional<StudySession> findByIdForUpdate(@Param("id") Integer id);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE study_sessions SET status = 'SKIPPED' WHERE schedule_id = :scheduleId AND status = 'PLANNED' AND (session_date < CAST(:today AS DATE) OR (session_date = CAST(:today AS DATE) AND end_time < CAST(:now AS TIME)))", nativeQuery = true)
    void updateStatusToSkippedIfMissed(@Param("scheduleId") Integer scheduleId, @Param("today") LocalDate today, @Param("now") LocalTime now);

    List<StudySession> findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            Integer userId, LocalDate from, LocalDate to);

    List<StudySession> findByScheduleIdOrderBySessionDateAscStartTimeAsc(Integer scheduleId);

    void deleteByScheduleId(Integer scheduleId);

    void deleteByScheduleIdAndStatusNot(Integer scheduleId, String status);

    List<StudySession> findByScheduleIdAndStatus(Integer scheduleId, String status);

    @Modifying
    @Query("DELETE FROM StudySession s WHERE s.task.id = :taskId AND s.status <> 'COMPLETED'")
    void deleteByTaskIdAndStatusNotCompleted(@Param("taskId") Integer taskId);

    // Dashboard queries
    List<StudySession> findBySchedule_User_IdAndStatusAndSessionDateBetween(
            Integer userId, String status, LocalDate start, LocalDate end);

    @Query("SELECT s.status, COUNT(s) FROM StudySession s WHERE s.schedule.user.id = :userId GROUP BY s.status")
    List<Object[]> countByStatusGrouped(@Param("userId") Integer userId);

    @Query("SELECT DISTINCT s.sessionDate FROM StudySession s WHERE s.schedule.user.id = :userId AND s.status = 'COMPLETED' ORDER BY s.sessionDate DESC")
    List<LocalDate> findDistinctCompletedSessionDates(@Param("userId") Integer userId);

    @Query(value = "SELECT s.* FROM study_sessions s " +
           "INNER JOIN study_schedules sch ON s.schedule_id = sch.id " +
           "WHERE sch.user_id = :userId " +
           "AND (s.session_date > CAST(:today AS DATE) OR (s.session_date = CAST(:today AS DATE) AND s.end_time > CAST(:now AS TIME))) " +
           "AND s.status IN ('PLANNED', 'IN_PROGRESS') " +
           "ORDER BY s.session_date ASC, s.start_time ASC", 
           nativeQuery = true)
    List<StudySession> findUpcomingSessions(@Param("userId") Integer userId, 
                                            @Param("today") LocalDate today, 
                                            @Param("now") LocalTime now, 
                                            org.springframework.data.domain.Pageable pageable);
}
