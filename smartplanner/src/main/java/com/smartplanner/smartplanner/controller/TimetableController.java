package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.timetable.TimetableResponse;
import com.smartplanner.smartplanner.service.TimetableService;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    // Generate timetable for a week
    // blockMinutes: 60 default; allowed: 30,60,90,120
    @PostMapping("/generate")
    public TimetableResponse generate(@RequestParam(defaultValue = "0") int weekOffset,
                                      @RequestParam(defaultValue = "60") int blockMinutes,
                                      HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return timetableService.generate(userId, weekOffset, blockMinutes);
    }

    // View timetable for a week
    @GetMapping
    public TimetableResponse get(@RequestParam(defaultValue = "0") int weekOffset,
                                 HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return timetableService.getTimetable(userId, weekOffset);
    }
}
