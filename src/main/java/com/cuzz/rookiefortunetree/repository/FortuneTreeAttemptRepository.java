package com.cuzz.rookiefortunetree.repository;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Repository;
import com.cuzz.rookiefortunetree.mapper.FortuneTreeAttemptMapper;
import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.AttemptStatus;
import com.cuzz.rookiefortunetree.repository.model.FortuneTreeAttemptRecord;
import com.cuzz.rookiefortunetree.util.BitSetCodec;

import java.util.BitSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Repository
public final class FortuneTreeAttemptRepository {
    private final FortuneTreeAttemptMapper mapper;
    private final Logger logger;

    @Autowired
    public FortuneTreeAttemptRepository(@Autowired(required = false) FortuneTreeAttemptMapper mapper,
                                        Logger logger) {
        this.mapper = mapper;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return mapper != null;
    }

    public void createTables() {
        if (!isAvailable()) {
            logger.fine("[FortuneTree] MyBatis mapper unavailable, skip attempt table init.");
            return;
        }
        try {
            mapper.createTableIfNotExists();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to init attempt table.", ex);
        }
    }

    public AttemptState find(UUID uuid, String cycleId) {
        if (!isAvailable() || uuid == null || cycleId == null || cycleId.isBlank()) {
            return null;
        }
        try {
            FortuneTreeAttemptRecord record = mapper.selectById(uuid.toString(), cycleId);
            if (record == null) {
                return null;
            }
            return toState(uuid, record);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to load attempt record.", ex);
            return null;
        }
    }

    public void upsert(AttemptState attempt) {
        if (!isAvailable() || attempt == null || attempt.getUuid() == null) {
            return;
        }
        String uuid = attempt.getUuid().toString();
        String cycleId = attempt.getCycleId();
        if (cycleId == null || cycleId.isBlank()) {
            return;
        }
        FortuneTreeAttemptRecord record = toRecord(uuid, attempt);
        record.setUpdatedAtMillis(System.currentTimeMillis());
        try {
            FortuneTreeAttemptRecord existing = mapper.selectById(uuid, cycleId);
            if (existing == null) {
                mapper.insert(record);
            } else {
                mapper.update(record);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "[FortuneTree] Failed to upsert attempt record.", ex);
            throw new IllegalStateException("Failed to upsert attempt record.", ex);
        }
    }

    private AttemptState toState(UUID uuid, FortuneTreeAttemptRecord record) {
        AttemptState attempt = new AttemptState(uuid, record.getCycleId());
        attempt.setUsedCount(record.getUsedCount());
        attempt.setLevel(record.getLevel());
        attempt.setDeposit(record.getDeposit());
        attempt.setRewardMax(record.getRewardMax());
        attempt.setSeed(record.getSeed());
        attempt.setBubbleCount(record.getBubbleCount());
        attempt.setRerollCount(record.getRerollCount());
        attempt.setCreatedAtMillis(record.getCreatedAtMillis());
        attempt.setStatus(parseStatus(record.getStatus()));

        BitSet bits = BitSetCodec.fromBase64(record.getCollectBits());
        for (int i = bits.nextSetBit(0); i >= 0 && i < attempt.getBubbleCount(); i = bits.nextSetBit(i + 1)) {
            attempt.markCollected(i);
        }
        return attempt;
    }

    private FortuneTreeAttemptRecord toRecord(String uuid, AttemptState attempt) {
        FortuneTreeAttemptRecord record = new FortuneTreeAttemptRecord();
        record.setUuid(uuid);
        record.setCycleId(attempt.getCycleId());
        record.setStatus(attempt.getStatus() == null ? AttemptStatus.IDLE.name() : attempt.getStatus().name());
        record.setUsedCount(attempt.getUsedCount());
        record.setLevel(attempt.getLevel());
        record.setDeposit(attempt.getDeposit());
        record.setRewardMax(attempt.getRewardMax());
        record.setSeed(attempt.getSeed());
        record.setBubbleCount(attempt.getBubbleCount());
        record.setRerollCount(attempt.getRerollCount());
        record.setCreatedAtMillis(attempt.getCreatedAtMillis());
        record.setCollectBits(BitSetCodec.toBase64(attempt.snapshotCollected()));
        return record;
    }

    private AttemptStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return AttemptStatus.IDLE;
        }
        try {
            return AttemptStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AttemptStatus.IDLE;
        }
    }
}
