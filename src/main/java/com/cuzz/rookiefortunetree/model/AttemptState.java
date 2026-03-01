package com.cuzz.rookiefortunetree.model;

import java.util.BitSet;
import java.util.UUID;

public final class AttemptState {
    private final UUID uuid;
    private String cycleId;
    private int usedCount;
    private AttemptStatus status = AttemptStatus.IDLE;
    private int level;
    private int deposit;
    private int rewardMax;
    private long seed;
    private int bubbleCount;
    private int rerollCount;
    private long createdAtMillis;
    private final BitSet collected = new BitSet();

    public AttemptState(UUID uuid, String cycleId) {
        this.uuid = uuid;
        this.cycleId = cycleId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getCycleId() {
        return cycleId;
    }

    public void setCycleId(String cycleId) {
        this.cycleId = cycleId;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = Math.max(0, usedCount);
    }

    public void incrementUsedCount() {
        usedCount++;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public void setStatus(AttemptStatus status) {
        this.status = status == null ? AttemptStatus.IDLE : status;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, level);
    }

    public int getDeposit() {
        return deposit;
    }

    public void setDeposit(int deposit) {
        this.deposit = Math.max(0, deposit);
    }

    public int getRewardMax() {
        return rewardMax;
    }

    public void setRewardMax(int rewardMax) {
        this.rewardMax = Math.max(0, rewardMax);
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public int getBubbleCount() {
        return bubbleCount;
    }

    public void setBubbleCount(int bubbleCount) {
        this.bubbleCount = Math.max(0, bubbleCount);
        if (this.bubbleCount <= 0) {
            resetCollected();
            return;
        }
        int length = collected.length();
        if (length > this.bubbleCount) {
            collected.clear(this.bubbleCount, length);
        }
    }

    public int getRerollCount() {
        return rerollCount;
    }

    public void setRerollCount(int rerollCount) {
        this.rerollCount = Math.max(0, rerollCount);
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = Math.max(0, createdAtMillis);
    }

    public int collectedCount() {
        return collected.cardinality();
    }

    public boolean isCollected(int index) {
        return index >= 0 && index < bubbleCount && collected.get(index);
    }

    public boolean markCollected(int index) {
        if (index < 0 || index >= bubbleCount) {
            return false;
        }
        if (collected.get(index)) {
            return false;
        }
        collected.set(index);
        return true;
    }

    public boolean unmarkCollected(int index) {
        if (index < 0 || index >= bubbleCount) {
            return false;
        }
        if (!collected.get(index)) {
            return false;
        }
        collected.clear(index);
        return true;
    }

    public void markAllCollected() {
        if (bubbleCount <= 0) {
            return;
        }
        collected.set(0, bubbleCount);
    }

    public boolean isAllCollected() {
        return bubbleCount > 0 && collected.cardinality() >= bubbleCount;
    }

    public void resetCollected() {
        collected.clear();
    }

    public BitSet snapshotCollected() {
        return (BitSet) collected.clone();
    }

    public void startNewAttempt(int level, int deposit, int rewardMax, long seed, int bubbleCount) {
        this.level = level;
        this.deposit = Math.max(0, deposit);
        this.rewardMax = Math.max(0, rewardMax);
        this.seed = seed;
        this.bubbleCount = Math.max(0, bubbleCount);
        this.rerollCount = 0;
        this.createdAtMillis = System.currentTimeMillis();
        this.status = AttemptStatus.PENDING;
        resetCollected();
    }

    public void reroll(long seed, int bubbleCount) {
        this.seed = seed;
        this.bubbleCount = Math.max(0, bubbleCount);
        this.rerollCount++;
        this.createdAtMillis = System.currentTimeMillis();
        resetCollected();
    }
}
