package com.smartplanner.smartplanner.dto.course;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CourseResponse(
        Integer id,
        String name,
        String priority,
        LocalDate deadlineDate,
        BigDecimal totalHours,
        String status,
        Boolean isDeleted
) {}
