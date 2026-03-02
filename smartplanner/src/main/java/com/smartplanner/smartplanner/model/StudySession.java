package com.smartplanner.smartplanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "study_sessions", indexes = {
                @Index(name = "idx_sessions_schedule", columnList = "schedule_id"),
                @Index(name = "idx_sessions_date", columnList = "session_date")
})
public class StudySession {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        // FK: study_sessions.schedule_id -> study_schedules.id
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "schedule_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_schedule"))
        @JsonIgnore
        private StudySchedule schedule;

        // FK: study_sessions.course_id -> courses.id
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_course"))
        @JsonIgnore
        private Course course;

        // FK: study_sessions.task_id -> tasks.id (nullable)
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "fk_session_task"))
        @JsonIgnore
        private Task task;

        @Column(name = "session_date", nullable = false)
        private LocalDate sessionDate;

        @Column(name = "start_time", nullable = false)
        private LocalTime startTime;

        @Column(name = "end_time", nullable = false)
        private LocalTime endTime;

        @Column(name = "duration_minutes", nullable = false)
        private Integer durationMinutes;

        @Column(nullable = false, length = 20)
        private String status = "PLANNED"; // PLANNED, IN_PROGRESS, COMPLETED, SKIPPED

        @Column(precision = 5, scale = 2)
        private BigDecimal actualHoursLogged; // null if not completed

        private LocalDateTime completedAt; // timestamp when marked as COMPLETED

        @Column(nullable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        private LocalDateTime updatedAt;

        // getters/setters
        public Integer getId() {
                return id;
        }

        public void setId(Integer id) {
                this.id = id;
        }

        public StudySchedule getSchedule() {
                return schedule;
        }

        public void setSchedule(StudySchedule schedule) {
                this.schedule = schedule;
        }

        public Course getCourse() {
                return course;
        }

        public void setCourse(Course course) {
                this.course = course;
        }

        public Task getTask() {
                return task;
        }

        public void setTask(Task task) {
                this.task = task;
        }

        public LocalDate getSessionDate() {
                return sessionDate;
        }

        public void setSessionDate(LocalDate sessionDate) {
                this.sessionDate = sessionDate;
        }

        public LocalTime getStartTime() {
                return startTime;
        }

        public void setStartTime(LocalTime startTime) {
                this.startTime = startTime;
        }

        public LocalTime getEndTime() {
                return endTime;
        }

        public void setEndTime(LocalTime endTime) {
                this.endTime = endTime;
        }

        public Integer getDurationMinutes() {
                return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
                this.durationMinutes = durationMinutes;
        }

        public String getStatus() {
                return status;
        }

        public void setStatus(String status) {
                this.status = status;
        }

        public LocalDateTime getCreatedAt() {
                return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
                return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
        }

        public BigDecimal getActualHoursLogged() {
                return actualHoursLogged;
        }

        public void setActualHoursLogged(BigDecimal actualHoursLogged) {
                this.actualHoursLogged = actualHoursLogged;
        }

        public LocalDateTime getCompletedAt() {
                return completedAt;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
                this.completedAt = completedAt;
        }
}
