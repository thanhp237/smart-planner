package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.dashboard.CompleteSessionRequest;
import com.smartplanner.smartplanner.model.StudySession;
import com.smartplanner.smartplanner.repository.StudySessionRepository;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final StudySessionRepository sessionRepo;

    public SessionController(StudySessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<String> completeSession(
            @PathVariable Integer id,
            @RequestBody CompleteSessionRequest request,
            HttpSession httpSession) {
        Integer userId = SessionUtil.requireUserId(httpSession);

        StudySession session = sessionRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        // Verify ownership
        if (!session.getSchedule().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session");
        }

        session.setStatus("COMPLETED");
        session.setActualHoursLogged(request.actualHoursLogged());
        session.setCompletedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);

        return ResponseEntity.ok("Session marked as completed");
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

        session.setStatus("IN_PROGRESS");
        session.setUpdatedAt(LocalDateTime.now());

        sessionRepo.save(session);

        return ResponseEntity.ok("Session started");
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
