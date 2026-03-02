package com.smartplanner.smartplanner.dto.preference;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class StudyPreferenceUpsertRequest {

    // optional
    private LocalDate planStartDate;
    private LocalDate planEndDate;

    @NotNull
    @DecimalMin(value = "0.5", message = "maxHoursPerDay must be >= 0.5")
    @Digits(integer = 2, fraction = 2)
    private BigDecimal maxHoursPerDay;

    @NotBlank
    @Pattern(
            regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
            message = "allowedDays must be comma-separated values of MON..SUN (e.g., MON,TUE,FRI)"
    )
    private String allowedDays;

    // getters/setters
    public LocalDate getPlanStartDate() { return planStartDate; }
    public void setPlanStartDate(LocalDate planStartDate) { this.planStartDate = planStartDate; }

    public LocalDate getPlanEndDate() { return planEndDate; }
    public void setPlanEndDate(LocalDate planEndDate) { this.planEndDate = planEndDate; }

    public BigDecimal getMaxHoursPerDay() { return maxHoursPerDay; }
    public void setMaxHoursPerDay(BigDecimal maxHoursPerDay) { this.maxHoursPerDay = maxHoursPerDay; }

    public String getAllowedDays() { return allowedDays; }
    public void setAllowedDays(String allowedDays) { this.allowedDays = allowedDays; }
}
