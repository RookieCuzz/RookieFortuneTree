package com.cuzz.rookiefortunetree.repository.model;

public final class FortuneTreePlayerRecord {
    private String uuid;
    private int level;
    private int exp;
    private int freePicks;
    private boolean firstDone;
    private long totalDeposit;
    private long totalReward;
    private long critCount;
    private long updatedAtMillis;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public int getFreePicks() {
        return freePicks;
    }

    public void setFreePicks(int freePicks) {
        this.freePicks = freePicks;
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

    public void setTotalDeposit(long totalDeposit) {
        this.totalDeposit = totalDeposit;
    }

    public long getTotalReward() {
        return totalReward;
    }

    public void setTotalReward(long totalReward) {
        this.totalReward = totalReward;
    }

    public long getCritCount() {
        return critCount;
    }

    public void setCritCount(long critCount) {
        this.critCount = critCount;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }
}

