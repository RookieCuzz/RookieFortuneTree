package com.cuzz.rookiefortunetree.model;

import java.util.UUID;

public final class PlayerState {
    private final UUID uuid;
    private int level;
    private int exp;
    private int freePicks;
    private boolean firstDone;
    private long totalDeposit;
    private long totalReward;
    private long critCount;

    public PlayerState(UUID uuid) {
        this.uuid = uuid;
        this.level = 1;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = Math.max(0, exp);
    }

    public int getFreePicks() {
        return freePicks;
    }

    public void setFreePicks(int freePicks) {
        this.freePicks = Math.max(0, freePicks);
    }

    public boolean isFirstDone() {
        return firstDone;
    }

    public void setFirstDone(boolean firstDone) {
        this.firstDone = firstDone;
    }

    public long getTotalDeposit() {
        return totalDeposit;
    }

    public void addTotalDeposit(long delta) {
        if (delta > 0) {
            this.totalDeposit += delta;
        }
    }

    public long getTotalReward() {
        return totalReward;
    }

    public void addTotalReward(long delta) {
        if (delta > 0) {
            this.totalReward += delta;
        }
    }

    public long getCritCount() {
        return critCount;
    }

    public void addCritCount(long delta) {
        if (delta > 0) {
            this.critCount += delta;
        }
    }
}

