package com.cuzz.rookiefortunetree.repository.model;

public final class FortuneTreeAttemptRecord {
    private String uuid;
    private String cycleId;
    private String status;
    private int usedCount;
    private int level;
    private int deposit;
    private int rewardMax;
    private long seed;
    private int bubbleCount;
    private int rerollCount;
    private long createdAtMillis;
    private String collectBits;
    private long updatedAtMillis;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getCycleId() {
        return cycleId;
    }

    public void setCycleId(String cycleId) {
        this.cycleId = cycleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getDeposit() {
        return deposit;
    }

    public void setDeposit(int deposit) {
        this.deposit = deposit;
    }

    public int getRewardMax() {
        return rewardMax;
    }

    public void setRewardMax(int rewardMax) {
        this.rewardMax = rewardMax;
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
        this.bubbleCount = bubbleCount;
    }

    public int getRerollCount() {
        return rerollCount;
    }

    public void setRerollCount(int rerollCount) {
        this.rerollCount = rerollCount;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    public String getCollectBits() {
        return collectBits;
    }

    public void setCollectBits(String collectBits) {
        this.collectBits = collectBits;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }
}

