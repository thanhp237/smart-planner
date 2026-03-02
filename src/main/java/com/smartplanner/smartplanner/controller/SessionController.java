package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.dashboard.CompleteSessionRequest;
import com.smartplanner.smartplanner.dto.dashboard.SessionReflectionResponse;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.repository.StudySessionRepository;
import com.smartplanner.smartplanner.service.ReflectionAiService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;

import com.smartplanner.smartplanner.repository.TaskRepository; // Added import
import com.smartplanner.smartplanner.model.Task; // Added import
import java.math.BigDecimal; // Added import

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final StudySessionRepository sessionRepo;
    private final TaskRepository taskRepo; // Added repository
    private final ReflectionAiService reflectionAiService;

    public SessionController(StudySessionRepository sessionRepo,
                             TaskRepository taskRepo,
                             ReflectionAiService reflectionAiService) { // Updated constructor
        this.sessionRepo = sessionRepo;
        this.taskRepo = taskRepo;
        this.reflectionAiService = reflectionAiService;
    }

    @Transactional
    @PutMapping("/{id}/complete")
    public ResponseEntity<Object> completeSession(
            @PathVariable Integer id,
            @RequestBody CompleteSessionRequest request,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        // Lock the session row to prevent double submission race conditions
        StudySession session = sessionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        // Verify ownership
        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        if ("COMPLETED".equals(session.getStatus())) {
            boolean changed = false;
            if (request.note() != null && !request.note().trim().isEmpty()) {
                session.setNote(request.note().trim());
                changed = true;
            }
            if (request.difficulty() != null && !request.difficulty().trim().isEmpty()) {
                session.setDifficulty(request.difficulty().trim());
                changed = true;
            }
            if (session.getActualHoursLogged() == null) {
                int actualMinutes = computeActualMinutes(session, session.getCompletedAt());
                BigDecimal actualHours = BigDecimal.valueOf(actualMinutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
                session.setActualHoursLogged(actualHours);
                changed = true;
            }
            if (!changed) {
                return ResponseEntity.ok("Session already completed");
            }

            String safeNote = session.getNote() == null ? "" : session.getNote().trim();
            if (safeNote.isBlank()) {
                setAiDoneEmpty(session);
            } else {
                setAiPending(session);
            }
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepo.save(session);
            if (!safeNote.isBlank()) {
                scheduleReflectionAnalysisAfterCommit(session);
            }
            return ResponseEntity.ok("Session updated");
        }

        session.setStatus("COMPLETED");
        session.setDifficulty(request.difficulty()); // Save difficulty rating
        session.setNote(request.note());
        LocalDateTime completedAt = LocalDateTime.now();
        session.setCompletedAt(completedAt);
        if (session.getStartedAt() == null) {
            session.setStartedAt(completedAt);
        }
        int actualMinutes = computeActualMinutes(session, completedAt);
        BigDecimal actualHours = BigDecimal.valueOf(actualMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        session.setActualHoursLogged(actualHours);
        session.setUpdatedAt(LocalDateTime.now());
        String safeNote = session.getNote() == null ? "" : session.getNote().trim();
        if (safeNote.isBlank()) {
            setAiDoneEmpty(session);
        } else {
            setAiPending(session);
        }

        sessionRepo.save(session);
        // Gamification removed: no XP/streak update

        // Update Task Remaining Hours
        Task task = session.getTask();
        if (task != null) {
            BigDecimal hoursLogged = session.getActualHoursLogged();
            if (hoursLogged == null) {
                hoursLogged = BigDecimal.valueOf(session.getDurationMinutes())
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            }
            
            BigDecimal newRemaining = task.getRemainingHours().subtract(hoursLogged);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                newRemaining = BigDecimal.ZERO;
            }
            task.setRemainingHours(newRemaining);
            
            // Auto-complete task if remaining is zero? Maybe not automatically, let user decide.
            // But we update progress.
            taskRepo.save(task);
        }

        if (!safeNote.isBlank()) {
            scheduleReflectionAnalysisAfterCommit(session);
        }
        return ResponseEntity.ok("Session completed");
    }

    @GetMapping("/{id}/reflection")
    public ResponseEntity<SessionReflectionResponse> getReflection(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        String courseName;
        if (session.getCourse() == null) {
            courseName = "No subject assigned";
        } else if (Boolean.TRUE.equals(session.getCourse().getIsDeleted())) {
            courseName = "Deleted subject";
        } else {
            courseName = session.getCourse().getName();
        }
        String taskTitle = session.getTask() != null ? session.getTask().getTitle() : null;

        return ResponseEntity.ok(new SessionReflectionResponse(
                session.getId(),
                courseName,
                taskTitle,
                session.getDifficulty(),
                session.getNote(),
                session.getAiStatus(),
                session.getAiQualityScore(),
                session.getAiSummary(),
                session.getAiNextAction(),
                session.getAiRevisionSuggestion(),
                session.getAiAnalyzedAt(),
                session.getAiError()
        ));
    }

    private void setAiPending(StudySession session) {
        session.setAiStatus("PENDING");
        session.setAiQualityScore(null);
        session.setAiSummary(null);
        session.setAiNextAction(null);
        session.setAiRevisionSuggestion(null);
        session.setAiAnalyzedAt(null);
        session.setAiError(null);
    }

    private void setAiDoneEmpty(StudySession session) {
        session.setAiStatus("DONE");
        session.setAiQualityScore(0);
        session.setAiSummary("");
        session.setAiNextAction("");
        session.setAiRevisionSuggestion("");
        session.setAiAnalyzedAt(LocalDateTime.now());
        session.setAiError(null);
    }

    private void scheduleReflectionAnalysisAfterCommit(StudySession session) {
        Integer sessionId = session.getId();
        String note = session.getNote();
        String difficulty = session.getDifficulty();
        String courseName;
        if (session.getCourse() == null) {
            courseName = "No subject assigned";
        } else if (Boolean.TRUE.equals(session.getCourse().getIsDeleted())) {
            courseName = "Deleted subject";
        } else {
            courseName = session.getCourse().getName();
        }
        String taskTitle = session.getTask() != null ? session.getTask().getTitle() : null;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reflectionAiService.analyzeSession(sessionId, note, difficulty, courseName, taskTitle);
            }
        });
    }

    @PutMapping("/{id}/start")
    public ResponseEntity<String> startSession(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        if (session.getSessionDate() != null && session.getEndTime() != null) {
            LocalDateTime slotEnd = LocalDateTime.of(session.getSessionDate(), session.getEndTime());
            if (!slotEnd.isAfter(LocalDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot expired");
            }
        }

        session.setStatus("IN_PROGRESS");
        if (session.getStartedAt() == null) {
            session.setStartedAt(LocalDateTime.now());
        }
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);

        return ResponseEntity.ok("Session started");
    }

    private int computeActualMinutes(StudySession session, LocalDateTime completedAt) {
        LocalDateTime startedAt = session.getStartedAt();
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        long minutes = Duration.between(startedAt, completedAt).toMinutes();
        if (minutes < 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, minutes);
    }

    @PutMapping("/{id}/skip")
    public ResponseEntity<String> skipSession(
            @PathVariable Integer id,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        session.setStatus("SKIPPED");
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);

        return ResponseEntity.ok("Session skipped");
    }
}
