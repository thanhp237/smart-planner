package com.smartplanner.smartplanner.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

public class WeekUtil {
    private WeekUtil() {
    }

    // weekOffset=0 => tuần hiện tại, -1 => tuần trước, +1 => tuần sau
    public static LocalDate weekStart(LocalDate anyDate, int weekOffset) {
        LocalDate shifted = anyDate.plusDays((long) weekOffset * 7);
        int diff = shifted.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue(); // Mon=1
        return shifted.minusDays(diff);
    }

    public static LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }
}
