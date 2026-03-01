package com.cuzz.rookiefortunetree.repository;

import com.cuzz.rookiefortunetree.mapper.FortuneTreeAttemptMapper;
import com.cuzz.rookiefortunetree.mapper.FortuneTreePlayerMapper;
import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryFailurePropagationTest {

    @Test
    void playerUpsert_shouldThrowWhenMapperWriteFails() {
        FortuneTreePlayerMapper mapper = (FortuneTreePlayerMapper) Proxy.newProxyInstance(
                FortuneTreePlayerMapper.class.getClassLoader(),
                new Class<?>[]{FortuneTreePlayerMapper.class},
                (proxy, method, args) -> {
                    if ("selectByUuid".equals(method.getName())) {
                        return null;
                    }
                    if ("insert".equals(method.getName())) {
                        throw new RuntimeException("player table unavailable");
                    }
                    return 0;
                }
        );

        FortuneTreePlayerRepository repository = new FortuneTreePlayerRepository(mapper, Logger.getLogger("test-player-repo"));
        PlayerState state = new PlayerState(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> repository.upsert(state));
    }

    @Test
    void attemptUpsert_shouldThrowWhenMapperWriteFails() {
        FortuneTreeAttemptMapper mapper = (FortuneTreeAttemptMapper) Proxy.newProxyInstance(
                FortuneTreeAttemptMapper.class.getClassLoader(),
                new Class<?>[]{FortuneTreeAttemptMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return null;
                    }
                    if ("insert".equals(method.getName())) {
                        throw new RuntimeException("attempt table unavailable");
                    }
                    return 0;
                }
        );

        FortuneTreeAttemptRepository repository = new FortuneTreeAttemptRepository(mapper, Logger.getLogger("test-attempt-repo"));
        AttemptState attempt = new AttemptState(UUID.randomUUID(), "2026-03-01");
        attempt.startNewAttempt(1, 100, 200, 123L, 8);

        assertThrows(IllegalStateException.class, () -> repository.upsert(attempt));
    }
}
