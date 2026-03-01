package com.cuzz.rookiefortunetree.service;

import com.cuzz.rookiefortunetree.model.LevelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FortuneTreeServiceExpGainTest {

    @Test
    void shouldUsePaidDepositWhenHigherThanLevelBase() {
        LevelConfig level = new LevelConfig(1, "LV1", 0, 240, 100, 8, 12, 0.08, 0.55, 0.80);
        assertEquals(100, FortuneTreeService.computeExpGain(level, 100));
    }

    @Test
    void shouldUseLevelBaseWhenFreePickMakesPaidDepositZero() {
        LevelConfig level = new LevelConfig(2, "LV2", 100, 264, 200, 8, 12, 0.08, 0.55, 0.80);
        assertEquals(100, FortuneTreeService.computeExpGain(level, 0));
    }

    @Test
    void shouldKeepMinimumOneExpToAvoidNoProgress() {
        LevelConfig level = new LevelConfig(1, "LV1", 0, 240, 100, 8, 12, 0.08, 0.55, 0.80);
        assertEquals(1, FortuneTreeService.computeExpGain(level, 0));
    }
}

