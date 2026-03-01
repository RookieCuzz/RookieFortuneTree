package com.cuzz.rookiefortunetree.storage;

import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.PlayerState;

import java.util.UUID;

public interface FortuneTreeStore {
    PlayerState getOrCreatePlayer(UUID uuid);

    AttemptState getOrCreateAttempt(UUID uuid, String cycleId);

    void savePlayer(PlayerState state);

    default void savePlayer(PlayerState state, String playerName) {
        savePlayer(state);
    }

    void saveAttempt(AttemptState attempt);

    boolean markCollected(AttemptState attempt, int index);

    void markAllCollected(AttemptState attempt);

    void evictPlayer(UUID uuid);
}
