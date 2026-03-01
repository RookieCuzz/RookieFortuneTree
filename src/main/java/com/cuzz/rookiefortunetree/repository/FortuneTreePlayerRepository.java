package com.cuzz.rookiefortunetree.repository;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Repository;
import com.cuzz.rookiefortunetree.mapper.FortuneTreePlayerMapper;
import com.cuzz.rookiefortunetree.model.PlayerState;
import com.cuzz.rookiefortunetree.repository.model.FortuneTreePlayerRecord;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Repository
public final class FortuneTreePlayerRepository {
    private final FortuneTreePlayerMapper mapper;
    private final Logger logger;

    @Autowired
    public FortuneTreePlayerRepository(@Autowired(required = false) FortuneTreePlayerMapper mapper,
                                       Logger logger) {
        this.mapper = mapper;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return mapper != null;
    }

    public void createTables() {
        if (!isAvailable()) {
            logger.fine("[FortuneTree] MyBatis mapper unavailable, skip player table init.");
            return;
        }
        try {
            mapper.createTableIfNotExists();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to init player table.", ex);
        }
    }

    public PlayerState find(UUID uuid) {
        if (!isAvailable() || uuid == null) {
            return null;
        }
        try {
            FortuneTreePlayerRecord record = mapper.selectByUuid(uuid.toString());
            if (record == null) {
                return null;
            }
            return toState(uuid, record);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to load player record.", ex);
            return null;
        }
    }

    public void upsert(PlayerState state) {
        if (!isAvailable() || state == null || state.getUuid() == null) {
            return;
        }
        String uuid = state.getUuid().toString();
        FortuneTreePlayerRecord record = toRecord(uuid, state);
        record.setUpdatedAtMillis(System.currentTimeMillis());
        try {
            FortuneTreePlayerRecord existing = mapper.selectByUuid(uuid);
            if (existing == null) {
                mapper.insert(record);
            } else {
                mapper.update(record);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to upsert player record.", ex);
        }
    }

    private PlayerState toState(UUID uuid, FortuneTreePlayerRecord record) {
        PlayerState state = new PlayerState(uuid);
        state.setLevel(record.getLevel());
        state.setExp(record.getExp());
        state.setFreePicks(record.getFreePicks());
        state.setFirstDone(record.isFirstDone());
        state.addTotalDeposit(record.getTotalDeposit());
        state.addTotalReward(record.getTotalReward());
        state.addCritCount(record.getCritCount());
        return state;
    }

    private FortuneTreePlayerRecord toRecord(String uuid, PlayerState state) {
        FortuneTreePlayerRecord record = new FortuneTreePlayerRecord();
        record.setUuid(uuid);
        record.setLevel(state.getLevel());
        record.setExp(state.getExp());
        record.setFreePicks(state.getFreePicks());
        record.setFirstDone(state.isFirstDone());
        record.setTotalDeposit(state.getTotalDeposit());
        record.setTotalReward(state.getTotalReward());
        record.setCritCount(state.getCritCount());
        return record;
    }
}

