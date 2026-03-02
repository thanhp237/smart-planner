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
                ? repo.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId)
                : repo.findByUserIdAndDayOfWeekOrderByStartTimeAsc(userId, dayOfWeek);

        return slots.stream().map(AvailabilityController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilitySlotResponse create(@Valid @RequestBody AvailabilitySlotCreateRequest req,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        // Load user entity
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // Basic validation: start < end
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be before endTime");
        }

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

        repo.delete(s);
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
