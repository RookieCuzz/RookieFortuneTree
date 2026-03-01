package com.cuzz.rookiefortunetree.storage;

import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.PlayerState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback store (no persistence).
 */
public final class MemoryFortuneTreeStore implements FortuneTreeStore {
    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final Map<UUID, AttemptState> attempts = new ConcurrentHashMap<>();

    @Override
    public PlayerState getOrCreatePlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerState::new);
    }

    @Override
    public AttemptState getOrCreateAttempt(UUID uuid, String cycleId) {
        AttemptState existing = attempts.computeIfAbsent(uuid, id -> new AttemptState(id, cycleId));
        if (cycleId != null && !cycleId.equals(existing.getCycleId())) {
            AttemptState refreshed = new AttemptState(uuid, cycleId);
            attempts.put(uuid, refreshed);
            return refreshed;
        }
        return existing;
    }

    @Override
    public void savePlayer(PlayerState state) {
    }

    @Override
    public void saveAttempt(AttemptState attempt) {
    }

    @Override
    public boolean markCollected(AttemptState attempt, int index) {
        return attempt != null && attempt.markCollected(index);
    }

    @Override
    public void markAllCollected(AttemptState attempt) {
        if (attempt != null) {
            attempt.markAllCollected();
        }
    }

    @Override
    public void evictPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        players.remove(uuid);
        attempts.remove(uuid);
    }
}
