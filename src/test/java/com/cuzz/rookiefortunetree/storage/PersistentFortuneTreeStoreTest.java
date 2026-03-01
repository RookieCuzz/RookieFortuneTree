package com.cuzz.rookiefortunetree.storage;

import com.cuzz.rookiefortunetree.mapper.FortuneTreePlayerMapper;
import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.AttemptStatus;
import com.cuzz.rookiefortunetree.model.PlayerState;
import com.cuzz.rookiefortunetree.repository.model.FortuneTreeAttemptRecord;
import com.cuzz.rookiefortunetree.repository.FortuneTreeAttemptRepository;
import com.cuzz.rookiefortunetree.repository.FortuneTreePlayerRepository;
import com.cuzz.rookiefortunetree.repository.model.FortuneTreePlayerRecord;
import com.cuzz.rookiefortunetree.mapper.FortuneTreeAttemptMapper;
import com.cuzz.starter.bukkitspring.redis.api.RedisService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PersistentFortuneTreeStoreTest {

    @Test
    public void shouldQueryRedisBeforeMybatisAndReloadAfterEvict() {
        UUID uuid = UUID.randomUUID();
        AtomicInteger redisQueryCount = new AtomicInteger();
        AtomicInteger mybatisQueryCount = new AtomicInteger();

        Map<String, Map<String, String>> redisHashes = new HashMap<>();
        redisHashes.put(playerKey(uuid), Map.of(
                "level", "7",
                "exp", "12",
                "free", "3",
                "firstDone", "1",
                "totalDeposit", "200",
                "totalReward", "300",
                "critCount", "5"
        ));

        RedisService redis = createRedisService(redisHashes, redisQueryCount);
        FortuneTreePlayerRepository playerRepository = new FortuneTreePlayerRepository(
                createPlayerMapper(new FortuneTreePlayerRecord(), mybatisQueryCount),
                Logger.getLogger("test-player-repo")
        );
        FortuneTreeAttemptRepository attemptRepository = new FortuneTreeAttemptRepository(null, Logger.getLogger("test-attempt-repo"));

        PersistentFortuneTreeStore store = new PersistentFortuneTreeStore(
                Logger.getLogger("test-store"),
                playerRepository,
                attemptRepository,
                redis,
                null
        );

        PlayerState first = store.getOrCreatePlayer(uuid);
        assertEquals(7, first.getLevel());
        assertEquals(1, redisQueryCount.get());
        assertEquals(0, mybatisQueryCount.get());

        PlayerState second = store.getOrCreatePlayer(uuid);
        assertEquals(7, second.getLevel());
        assertEquals(1, redisQueryCount.get());
        assertEquals(0, mybatisQueryCount.get());

        store.evictPlayer(uuid);
        PlayerState reloaded = store.getOrCreatePlayer(uuid);
        assertEquals(7, reloaded.getLevel());
        assertEquals(2, redisQueryCount.get());
        assertEquals(0, mybatisQueryCount.get());
    }

    @Test
    public void shouldFallbackToMybatisWhenRedisMiss() {
        UUID uuid = UUID.randomUUID();
        AtomicInteger redisQueryCount = new AtomicInteger();
        AtomicInteger mybatisQueryCount = new AtomicInteger();

        RedisService redis = createRedisService(Map.of(), redisQueryCount);

        FortuneTreePlayerRecord record = new FortuneTreePlayerRecord();
        record.setUuid(uuid.toString());
        record.setLevel(3);
        record.setExp(99);
        record.setFreePicks(1);
        record.setFirstDone(true);
        record.setTotalDeposit(500L);
        record.setTotalReward(650L);
        record.setCritCount(2L);

        FortuneTreePlayerRepository playerRepository = new FortuneTreePlayerRepository(
                createPlayerMapper(record, mybatisQueryCount),
                Logger.getLogger("test-player-repo")
        );
        FortuneTreeAttemptRepository attemptRepository = new FortuneTreeAttemptRepository(null, Logger.getLogger("test-attempt-repo"));

        PersistentFortuneTreeStore store = new PersistentFortuneTreeStore(
                Logger.getLogger("test-store"),
                playerRepository,
                attemptRepository,
                redis,
                null
        );

        PlayerState state = store.getOrCreatePlayer(uuid);
        assertEquals(3, state.getLevel());
        assertEquals(1, redisQueryCount.get());
        assertEquals(1, mybatisQueryCount.get());
    }

    @Test
    public void shouldFallbackToMybatisForAttemptWhenRedisMiss() {
        UUID uuid = UUID.randomUUID();
        String cycleId = "2026-03-01";
        AtomicInteger redisQueryCount = new AtomicInteger();
        AtomicInteger mybatisQueryCount = new AtomicInteger();

        RedisService redis = createRedisService(Map.of(), redisQueryCount);

        FortuneTreeAttemptRecord record = new FortuneTreeAttemptRecord();
        record.setUuid(uuid.toString());
        record.setCycleId(cycleId);
        record.setStatus(AttemptStatus.PENDING.name());
        record.setUsedCount(2);
        record.setLevel(5);
        record.setDeposit(120);
        record.setRewardMax(180);
        record.setSeed(123456L);
        record.setBubbleCount(4);
        record.setRerollCount(1);
        record.setCreatedAtMillis(1700000000000L);

        FortuneTreePlayerRepository playerRepository = new FortuneTreePlayerRepository(
                null,
                Logger.getLogger("test-player-repo")
        );
        FortuneTreeAttemptRepository attemptRepository = new FortuneTreeAttemptRepository(
                createAttemptMapper(record, mybatisQueryCount),
                Logger.getLogger("test-attempt-repo")
        );

        PersistentFortuneTreeStore store = new PersistentFortuneTreeStore(
                Logger.getLogger("test-store"),
                playerRepository,
                attemptRepository,
                redis,
                null
        );

        AttemptState first = store.getOrCreateAttempt(uuid, cycleId);
        assertEquals(2, first.getUsedCount());
        assertEquals(AttemptStatus.PENDING, first.getStatus());
        assertEquals(1, redisQueryCount.get());
        assertEquals(1, mybatisQueryCount.get());

        AttemptState second = store.getOrCreateAttempt(uuid, cycleId);
        assertEquals(2, second.getUsedCount());
        assertEquals(1, redisQueryCount.get());
        assertEquals(1, mybatisQueryCount.get());

        store.evictPlayer(uuid);
        AttemptState reloaded = store.getOrCreateAttempt(uuid, cycleId);
        assertEquals(2, reloaded.getUsedCount());
        assertEquals(2, redisQueryCount.get());
        assertEquals(1, mybatisQueryCount.get());
    }

    @Test
    public void shouldWritePlayerNameToRedisWhenProvided() {
        UUID uuid = UUID.randomUUID();
        AtomicInteger redisQueryCount = new AtomicInteger();
        Map<String, Map<String, String>> redisHashes = new HashMap<>();
        RedisService redis = createRedisService(redisHashes, redisQueryCount);

        FortuneTreePlayerRepository playerRepository = new FortuneTreePlayerRepository(
                null,
                Logger.getLogger("test-player-repo")
        );
        FortuneTreeAttemptRepository attemptRepository = new FortuneTreeAttemptRepository(
                null,
                Logger.getLogger("test-attempt-repo")
        );

        PersistentFortuneTreeStore store = new PersistentFortuneTreeStore(
                Logger.getLogger("test-store"),
                playerRepository,
                attemptRepository,
                redis,
                null
        );

        PlayerState state = new PlayerState(uuid);
        state.setLevel(4);
        state.setExp(88);
        store.savePlayer(state, "has23");

        Map<String, String> stored = redis.hgetAll(playerKey(uuid));
        assertNotNull(stored);
        assertEquals("has23", stored.get("name"));
        assertEquals("4", stored.get("level"));
        assertEquals("88", stored.get("exp"));
    }

    private static RedisService createRedisService(Map<String, Map<String, String>> hashes,
                                                   AtomicInteger hgetAllCount) {
        Map<String, Map<String, String>> redisData = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : hashes.entrySet()) {
            redisData.put(entry.getKey(), entry.getValue() == null ? Map.of() : new HashMap<>(entry.getValue()));
        }
        return (RedisService) Proxy.newProxyInstance(
                RedisService.class.getClassLoader(),
                new Class<?>[]{RedisService.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("isEnabled".equals(name)) {
                        return true;
                    }
                    if ("hgetAll".equals(name)) {
                        hgetAllCount.incrementAndGet();
                        String key = args == null || args.length == 0 ? "" : String.valueOf(args[0]);
                        Map<String, String> map = redisData.get(key);
                        return map == null ? Map.of() : map;
                    }
                    if ("hset".equals(name)) {
                        String key = args == null || args.length == 0 ? "" : String.valueOf(args[0]);
                        if (args != null && args.length > 1 && args[1] instanceof Map<?, ?> input) {
                            Map<String, String> map = new HashMap<>();
                            for (Map.Entry<?, ?> entry : input.entrySet()) {
                                if (entry.getKey() == null || entry.getValue() == null) {
                                    continue;
                                }
                                map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                            }
                            redisData.put(key, map);
                        }
                        return defaultValue(method.getReturnType());
                    }
                    if ("exists".equals(name)) {
                        return false;
                    }
                    if ("close".equals(name)) {
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static FortuneTreePlayerMapper createPlayerMapper(FortuneTreePlayerRecord record,
                                                               AtomicInteger selectCounter) {
        return (FortuneTreePlayerMapper) Proxy.newProxyInstance(
                FortuneTreePlayerMapper.class.getClassLoader(),
                new Class<?>[]{FortuneTreePlayerMapper.class},
                (proxy, method, args) -> {
                    if ("selectByUuid".equals(method.getName())) {
                        selectCounter.incrementAndGet();
                        return record;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static FortuneTreeAttemptMapper createAttemptMapper(FortuneTreeAttemptRecord record,
                                                                 AtomicInteger selectCounter) {
        return (FortuneTreeAttemptMapper) Proxy.newProxyInstance(
                FortuneTreeAttemptMapper.class.getClassLoader(),
                new Class<?>[]{FortuneTreeAttemptMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        selectCounter.incrementAndGet();
                        return record;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        return null;
    }

    private static String playerKey(UUID uuid) {
        return "ft:player:" + uuid;
    }
}
