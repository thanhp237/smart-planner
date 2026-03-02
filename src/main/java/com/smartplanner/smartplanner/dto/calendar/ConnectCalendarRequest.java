package com.smartplanner.smartplanner.dto.calendar;

import java.time.LocalDateTime;

public record ConnectCalendarRequest(
        String provider,
        String externalAccountId,
        String accessToken,
        String refreshToken,
        LocalDateTime tokenExpiry,
        String scope) {
}

