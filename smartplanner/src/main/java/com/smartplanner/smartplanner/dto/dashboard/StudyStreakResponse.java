package com.smartplanner.smartplanner.dto.dashboard;

import java.time.LocalDate;

public record StudyStreakResponse(
        Integer currentStreak,
        Integer longestStreak,
        Integer totalStudyDays,
        LocalDate lastStudyDate) {
}
