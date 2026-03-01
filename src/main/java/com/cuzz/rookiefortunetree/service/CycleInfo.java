package com.cuzz.rookiefortunetree.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CycleInfo(
        LocalDate cycleDate,
        String cycleId,
        LocalDateTime nextResetAt,
        Duration untilNextReset
) {
}

