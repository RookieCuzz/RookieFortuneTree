package com.cuzz.rookiefortunetree.service;

import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.model.Bubble;
import com.cuzz.rookiefortunetree.model.BubbleType;
import com.cuzz.rookiefortunetree.model.LevelConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public final class RewardGenerator {
    private final FortuneTreeConfig config;

    public RewardGenerator(FortuneTreeConfig config) {
        this.config = config;
    }

    public GenerationResult generate(LevelConfig level, int deposit, long seed, int bubbleCount) {
        if (level == null) {
            throw new IllegalArgumentException("level is null");
        }
        if (bubbleCount <= 0) {
            throw new IllegalArgumentException("bubbleCount must be > 0");
        }
        int safeDeposit = Math.max(0, deposit);
        int profitMin = Math.max(1, config.profitMin());
        int minTotal = safeDeposit + profitMin;
        // Each bubble must be >= 1, so total must be at least bubbleCount.
        minTotal = Math.max(minTotal, bubbleCount);
        int maxTotal = level.rewardMax();
        if (maxTotal < minTotal) {
            throw new IllegalArgumentException("rewardMax(" + maxTotal + ") < minTotal(" + minTotal + ")");
        }

        Random random = new Random(seed);
        int total = randIntInclusive(random, minTotal, maxTotal);

        boolean hasCrit = random.nextDouble() < clamp01(level.critChance());
        int critCount = hasCrit ? 1 : 0;
        int normalCount = bubbleCount - critCount;
        if (normalCount <= 0) {
            critCount = 0;
            normalCount = bubbleCount;
        }

        int critTotal = 0;
        if (critCount > 0) {
            double share = randDouble(random, clamp01(level.critShareMin()), clamp01(level.critShareMax()));
            int desired = (int) Math.round(total * share);
            int maxAllowed = total - normalCount; // leave at least 1 for each normal bubble
            critTotal = clampInt(desired, 1, Math.max(1, maxAllowed));
        }
        int normalTotal = total - critTotal;

        List<Bubble> bubbles = new ArrayList<>(bubbleCount);
        List<Integer> normalParts = splitPositive(random, normalTotal, normalCount);
        for (int amount : normalParts) {
            bubbles.add(new Bubble(-1, amount, BubbleType.NORMAL));
        }
        if (critCount > 0) {
            List<Integer> critParts = splitPositive(random, critTotal, critCount);
            for (int amount : critParts) {
                bubbles.add(new Bubble(-1, amount, BubbleType.CRIT));
            }
        }
        Collections.shuffle(bubbles, random);
        for (int i = 0; i < bubbles.size(); i++) {
            Bubble b = bubbles.get(i);
            bubbles.set(i, new Bubble(i, b.amount(), b.type()));
        }

        return new GenerationResult(safeDeposit, total, List.copyOf(bubbles));
    }

    public int pickBubbleCount(LevelConfig level, long seed, int maxAllowed) {
        if (level == null) {
            return 1;
        }
        int min = Math.max(1, level.bubbleMin());
        int max = Math.max(min, level.bubbleMax());
        int bound = Math.max(1, maxAllowed);
        int cappedMax = Math.min(max, bound);
        int cappedMin = Math.min(min, cappedMax);
        return randIntInclusive(new Random(seed ^ 0x9E3779B97F4A7C15L), cappedMin, cappedMax);
    }

    private List<Integer> splitPositive(Random random, int total, int parts) {
        if (parts <= 0) {
            return List.of();
        }
        if (total < parts) {
            throw new IllegalArgumentException("total(" + total + ") < parts(" + parts + ")");
        }
        if (parts == 1) {
            return List.of(total);
        }
        int remaining = total;
        int remainingParts = parts;
        List<Integer> result = new ArrayList<>(parts);
        for (int i = 0; i < parts - 1; i++) {
            remainingParts--;
            int maxForThis = remaining - remainingParts; // leave at least 1 for each remaining part
            int value = randIntInclusive(random, 1, maxForThis);
            result.add(value);
            remaining -= value;
        }
        result.add(remaining);
        return result;
    }

    private int randIntInclusive(Random random, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt((max - min) + 1);
    }

    private double randDouble(Random random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return min + (random.nextDouble() * (max - min));
    }

    private int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
