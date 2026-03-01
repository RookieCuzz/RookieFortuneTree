package com.cuzz.rookiefortunetree.service;

import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.starter.bukkitspring.time.api.TimeService;
import com.cuzz.starter.bukkitspring.time.api.TimeSetResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResetClockServiceTest {

    @Test
    void cycle_shouldShiftBeforeResetAt() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("daily.resetAt", "05:00"));

        ZoneId zone = ZoneId.of("UTC");
        Instant instant = Instant.parse("2026-02-27T04:00:00Z");
        ResetClockService service = new ResetClockService(config, Clock.fixed(instant, zone));

        CycleInfo info = service.now();
        assertEquals("2026-02-26", info.cycleId());
        assertEquals("2026-02-27", info.nextResetAt().toLocalDate().toString());
    }

    @Test
    void cycle_shouldBeTodayAfterResetAt() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("daily.resetAt", "05:00"));

        ZoneId zone = ZoneId.of("UTC");
        Instant instant = Instant.parse("2026-02-27T06:00:00Z");
        ResetClockService service = new ResetClockService(config, Clock.fixed(instant, zone));

        CycleInfo info = service.now();
        assertEquals("2026-02-27", info.cycleId());
    }

    @Test
    void cycle_shouldUseTimeStarterNowWhenAvailable() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("daily.resetAt", "05:00"));

        ZoneId zone = ZoneId.of("UTC");
        Clock fallbackClock = Clock.fixed(Instant.parse("2026-02-27T22:00:00Z"), zone);
        TimeService timeService = new StubTimeService(Instant.parse("2026-02-27T04:00:00Z"), true);
        ResetClockService service = new ResetClockService(config, timeService, fallbackClock);

        CycleInfo info = service.now();
        assertEquals("2026-02-26", info.cycleId());
    }

    private static final class StubTimeService implements TimeService {
        private final Instant instant;
        private final boolean enabled;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        private StubTimeService(Instant instant, boolean enabled) {
            this.instant = instant;
            this.enabled = enabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public com.cuzz.starter.bukkitspring.time.config.TimeSettings settings() {
            return null;
        }

        @Override
        public ExecutorService executor() {
            return executor;
        }

        @Override
        public Instant now() {
            return instant;
        }

        @Override
        public long currentTimeMillis() {
            return instant.toEpochMilli();
        }

        @Override
        public ZonedDateTime now(ZoneId zoneId) {
            return ZonedDateTime.ofInstant(instant, zoneId == null ? ZoneId.systemDefault() : zoneId);
        }

        @Override
        public String formatNow(String pattern, String zoneId) {
            return now(ZoneId.of(zoneId == null || zoneId.isBlank() ? "UTC" : zoneId)).toString();
        }

        @Override
        public Duration debugOffset() {
            return Duration.ZERO;
        }

        @Override
        public void setDebugOffset(Duration offset) {
        }

        @Override
        public TimeSetResult setSystemTime(Instant target) {
            return TimeSetResult.failure("unsupported", -1, "", "", "", target);
        }

        @Override
        public CompletableFuture<TimeSetResult> setSystemTimeAsync(Instant target) {
            return CompletableFuture.completedFuture(setSystemTime(target));
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
