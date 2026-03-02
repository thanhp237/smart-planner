package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.availability.*;
import com.smartplanner.smartplanner.model.AvailabilitySlot;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.AvailabilitySlotRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/settings/availability")
public class AvailabilityController {

    private final AvailabilitySlotRepository repo;
    private final UserRepository userRepo;

    public AvailabilityController(AvailabilitySlotRepository repo,
            UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<AvailabilitySlotResponse> list(@RequestParam(required = false) Short dayOfWeek,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        List<AvailabilitySlot> slots = (dayOfWeek == null)
                ? repo.findByUserIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(userId)
                : repo.findByUserIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(userId, dayOfWeek);

        return slots.stream().map(AvailabilityController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilitySlotResponse create(@Valid @RequestBody AvailabilitySlotCreateRequest req,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }
        validateOverlap(userId, req.getDayOfWeek(), req.getStartTime(), req.getEndTime(), null);

        AvailabilitySlot s = new AvailabilitySlot();
        s.setUser(user);
        s.setDayOfWeek(req.getDayOfWeek());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setActive(true);
        s.setCreatedAt(LocalDateTime.now());

        return toResponse(repo.save(s));
    }

    @PutMapping("/{id}")
    public AvailabilitySlotResponse update(@PathVariable Integer id,
            @Valid @RequestBody AvailabilitySlotUpdateRequest req,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        AvailabilitySlot s = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));

        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }
        if (Boolean.TRUE.equals(req.getActive())) {
            validateOverlap(userId, req.getDayOfWeek(), req.getStartTime(), req.getEndTime(), id);
        }

        s.setDayOfWeek(req.getDayOfWeek());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setActive(req.getActive());

        return toResponse(repo.save(s));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        AvailabilitySlot s = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));

        s.setActive(false);
        repo.save(s);
    }

    private void validateOverlap(Integer userId, Short dayOfWeek, LocalTime start, LocalTime end, Integer ignoreId) {
        List<AvailabilitySlot> existing = repo.findByUserIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(userId, dayOfWeek);
        for (AvailabilitySlot slot : existing) {
            if (ignoreId != null && ignoreId.equals(slot.getId())) continue;
            if (start.isBefore(slot.getEndTime()) && end.isAfter(slot.getStartTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Availability slot overlaps existing slot");
            }
        }
    }

    private static AvailabilitySlotResponse toResponse(AvailabilitySlot s) {
        return new AvailabilitySlotResponse(
                s.getId(),
                s.getDayOfWeek(),
                s.getStartTime(),
                s.getEndTime(),
                s.getActive());
    }
}
