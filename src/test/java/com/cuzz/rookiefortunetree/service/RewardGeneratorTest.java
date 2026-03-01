package com.cuzz.rookiefortunetree.service;

import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.model.Bubble;
import com.cuzz.rookiefortunetree.model.BubbleType;
import com.cuzz.rookiefortunetree.model.LevelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardGeneratorTest {

    @Test
    void generate_shouldRespectConstraints_andBeDeterministic() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);

        LevelConfig level = new LevelConfig(
                2,
                "LV2",
                100,
                264,
                0,
                8,
                12,
                0.2,
                0.55,
                0.80
        );

        long seed = 123456789L;
        int deposit = 100;
        int bubbleCount = 10;

        GenerationResult r1 = generator.generate(level, deposit, seed, bubbleCount);
        GenerationResult r2 = generator.generate(level, deposit, seed, bubbleCount);

        assertEquals(r1.rewardTotal(), r2.rewardTotal());
        assertEquals(r1.bubbles(), r2.bubbles());

        assertTrue(r1.rewardTotal() > deposit);
        assertTrue(r1.rewardTotal() <= level.rewardMax());
        assertEquals(bubbleCount, r1.bubbles().size());

        int sum = r1.bubbles().stream().mapToInt(Bubble::amount).sum();
        assertEquals(r1.rewardTotal(), sum);
        assertTrue(r1.bubbles().stream().allMatch(b -> b.amount() >= 1));
        assertTrue(r1.bubbles().stream().allMatch(b -> b.index() >= 0));
    }

    @Test
    void generate_shouldEnsureTotalAtLeastBubbleCount_whenDepositIsZero() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);

        LevelConfig level = new LevelConfig(
                1,
                "LV1",
                0,
                20,
                0,
                8,
                12,
                0.0,
                0.0,
                0.0
        );

        GenerationResult result = generator.generate(level, 0, 42L, 12);
        assertTrue(result.rewardTotal() >= 12);
        assertTrue(result.rewardTotal() <= level.rewardMax());
        assertEquals(12, result.bubbles().size());

        int sum = result.bubbles().stream().mapToInt(Bubble::amount).sum();
        assertEquals(result.rewardTotal(), sum);
        assertTrue(result.bubbles().stream().allMatch(b -> b.amount() >= 1));
    }

    @Test
    void generate_shouldFailWhenRewardMaxLessThanBubbleCount() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);

        LevelConfig level = new LevelConfig(1, "LV1", 0, 10, 0, 8, 12, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(level, 0, 1L, 12));
    }

    @Test
    void pickBubbleCount_shouldCapToMaxSlots() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);
        LevelConfig level = new LevelConfig(1, "LV1", 0, 240, 0, 8, 12, 0.1, 0.5, 0.8);

        int picked = generator.pickBubbleCount(level, 1L, 5);
        assertTrue(picked >= 1);
        assertTrue(picked <= 5);
    }

    @Test
    void generate_shouldFailOnBadConfig() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);
        LevelConfig level = new LevelConfig(1, "LV1", 0, 100, 0, 1, 1, 0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> generator.generate(level, 100, 1L, 1));
    }

    @Test
    void generate_shouldSupportMultipleCritBubblesInSingleAttempt() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);
        LevelConfig level = new LevelConfig(1, "LV1", 0, 240, 0, 8, 12, 0.6, 0.70, 0.95);

        long maxCritCount = 0L;
        for (long seed = 1; seed <= 64; seed++) {
            GenerationResult result = generator.generate(level, 0, seed, 12);
            long critCount = result.bubbles().stream().filter(b -> b.type() == BubbleType.CRIT).count();
            maxCritCount = Math.max(maxCritCount, critCount);
            int sum = result.bubbles().stream().mapToInt(Bubble::amount).sum();
            assertEquals(result.rewardTotal(), sum);
            assertEquals(12, result.bubbles().size());
        }
        assertTrue(maxCritCount > 1, "Expected multi-crit bubbles but maxCritCount=" + maxCritCount);
    }

    @Test
    void generate_shouldNotFailWhenAllBubblesCanBeCrit() {
        FortuneTreeConfig config = TestConfigs.fortuneTreeConfig(yaml -> yaml.set("profit.min", 1));
        RewardGenerator generator = new RewardGenerator(config);
        LevelConfig level = new LevelConfig(1, "LV1", 0, 240, 0, 8, 12, 1.0, 0.05, 0.10);

        for (long seed = 1; seed <= 32; seed++) {
            long currentSeed = seed;
            GenerationResult result = assertDoesNotThrow(() -> generator.generate(level, 0, currentSeed, 12));
            long critCount = result.bubbles().stream().filter(b -> b.type() == BubbleType.CRIT).count();
            assertEquals(12L, critCount);
            int sum = result.bubbles().stream().mapToInt(Bubble::amount).sum();
            assertEquals(result.rewardTotal(), sum);
            assertTrue(result.bubbles().stream().allMatch(b -> b.amount() >= 1));
        }
    }
}
