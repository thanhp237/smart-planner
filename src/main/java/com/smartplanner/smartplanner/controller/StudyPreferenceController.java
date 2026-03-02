package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.preference.StudyPreferenceResponse;
import com.smartplanner.smartplanner.dto.preference.StudyPreferenceUpsertRequest;
import com.smartplanner.smartplanner.model.StudyPreference;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.StudyPreferenceRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/api/settings/preference")
public class StudyPreferenceController {

    private final StudyPreferenceRepository prefRepo;
    private final UserRepository userRepo;

    public StudyPreferenceController(StudyPreferenceRepository prefRepo,
            UserRepository userRepo) {
        this.prefRepo = prefRepo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public StudyPreferenceResponse get(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        StudyPreference pref = prefRepo.findByUserId(userId)
                .orElseGet(() -> {
                    // Default preference auto-created when first requested
                    User user = userRepo.findById(userId).orElseThrow();
                    StudyPreference p = new StudyPreference();
                    p.setUser(user);
                    p.setAllowedDays("MON,TUE,WED,THU,FRI");
                    p.setMaxHoursPerDay(new java.math.BigDecimal("2.00"));
                    p.setBlockMinutes(60);
                    p.setCreatedAt(LocalDateTime.now());
                    return prefRepo.save(p);
                });

        return toResponse(pref);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public StudyPreferenceResponse upsert(@Valid @RequestBody StudyPreferenceUpsertRequest req,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        StudyPreference pref = prefRepo.findByUserId(userId).orElseGet(() -> {
            User user = userRepo.findById(userId).orElseThrow();
            StudyPreference p = new StudyPreference();
            p.setUser(user);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        pref.setPlanStartDate(req.getPlanStartDate());
        pref.setPlanEndDate(req.getPlanEndDate());
        pref.setMaxHoursPerDay(req.getMaxHoursPerDay());
        pref.setAllowedDays(req.getAllowedDays());
        if (!Set.of(30, 60, 90, 120).contains(req.getBlockMinutes())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blockMinutes must be one of 30,60,90,120");
        }
        pref.setBlockMinutes(req.getBlockMinutes());
        pref.setUpdatedAt(LocalDateTime.now());

        return toResponse(prefRepo.save(pref));
    }

    private static StudyPreferenceResponse toResponse(StudyPreference p) {
        Integer blockMinutes = p.getBlockMinutes() == null ? 60 : p.getBlockMinutes();
        return new StudyPreferenceResponse(
                p.getId(),
                p.getPlanStartDate(),
                p.getPlanEndDate(),
                p.getMaxHoursPerDay(),
                p.getAllowedDays(),
                blockMinutes);
    }
}
