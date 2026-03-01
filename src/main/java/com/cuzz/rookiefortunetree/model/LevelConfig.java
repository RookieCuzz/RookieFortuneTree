package com.cuzz.rookiefortunetree.model;

public record LevelConfig(
        int level,
        String name,
        int deposit,
        int rewardMax,
        int expToNext,
        int bubbleMin,
        int bubbleMax,
        double critChance,
        double critShareMin,
        double critShareMax
) {
    public String safeName() {
        return name == null || name.isBlank() ? ("LV" + level) : name;
    }
}

