package com.cuzz.rookiefortunetree.service;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Service;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.starter.bukkitspring.time.api.TimeService;
import com.cuzz.starter.bukkitspring.time.config.TimeSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public final class ResetClockService {
    private final FortuneTreeConfig config;
    private final TimeService timeService;
    private final Clock fallbackClock;

    @Autowired
    public ResetClockService(FortuneTreeConfig config,
                             @Autowired(required = false) TimeService timeService) {
        this(config, timeService, Clock.systemDefaultZone());
    }

    ResetClockService(FortuneTreeConfig config, Clock fallbackClock) {
        this(config, null, fallbackClock);
    }

    ResetClockService(FortuneTreeConfig config, TimeService timeService, Clock fallbackClock) {
        this.config = config;
        this.timeService = timeService;
        this.fallbackClock = fallbackClock;
    }

    public CycleInfo now() {
        LocalTime resetAt = config.resetAt();
        LocalDateTime now = LocalDateTime.ofInstant(resolveNowInstant(), resolveZoneId());
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        LocalDate cycleDate = time.isBefore(resetAt) ? today.minusDays(1) : today;
        LocalDateTime nextReset = time.isBefore(resetAt)
                ? LocalDateTime.of(today, resetAt)
                : LocalDateTime.of(today.plusDays(1), resetAt);
        Duration until = Duration.between(now, nextReset);
        if (until.isNegative()) {
            until = Duration.ZERO;
        }
        return new CycleInfo(cycleDate, cycleDate.toString(), nextReset, until);
    }

    private Instant resolveNowInstant() {
        if (isTimeServiceReady()) {
            try {
                return timeService.now();
            } catch (Exception ignored) {
            }
        }
        return Instant.now(fallbackClock);
    }

    private ZoneId resolveZoneId() {
        if (isTimeServiceReady()) {
            try {
                TimeSettings settings = timeService.settings();
                if (settings != null && settings.zoneId != null) {
                    return settings.zoneId;
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackClock.getZone();
    }

    private boolean isTimeServiceReady() {
        try {
            return timeService != null && timeService.isEnabled();
        } catch (Exception ignored) {
            return false;
        }
    }

    public String format(Duration duration) {
        if (duration == null) {
            return "00:00:00";
        }
        long seconds = Math.max(0, duration.getSeconds());
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
