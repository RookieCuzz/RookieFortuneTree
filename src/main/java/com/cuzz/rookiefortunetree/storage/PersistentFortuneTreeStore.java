package com.cuzz.rookiefortunetree.storage;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.bukkitspring.api.annotation.PostConstruct;
import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.AttemptStatus;
import com.cuzz.rookiefortunetree.model.PlayerState;
import com.cuzz.rookiefortunetree.repository.FortuneTreeAttemptRepository;
import com.cuzz.rookiefortunetree.repository.FortuneTreePlayerRepository;
import com.cuzz.starter.bukkitspring.caffeine.api.CaffeineService;
import com.cuzz.starter.bukkitspring.redis.api.RedisService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public final class PersistentFortuneTreeStore implements FortuneTreeStore {
    private static final String PLAYER_KEY = "ft:player:%s";
    private static final String ATTEMPT_KEY = "ft:attempt:%s:%s";
    private static final String COLLECT_KEY = "ft:collect:%s:%s";
    private static final long ATTEMPT_TTL_SECONDS = 60L * 60L * 24L * 30L;
    private static final long REDIS_RETRY_COOLDOWN_MILLIS = 60_000L;
    private static final long JVM_CACHE_EXPIRE_AFTER_ACCESS_MILLIS = 5L * 60L * 1000L;
    private static final String REDIS_HEALTH_KEY = "ft:health";
    private static final String PLAYER_JVM_CACHE_NAME = "rookie-ft-player-state";
    private static final String ATTEMPT_JVM_CACHE_NAME = "rookie-ft-attempt-state";

    private final Logger logger;
    private final RedisService redis;
    private final CaffeineService caffeineService;
    private final FortuneTreePlayerRepository playerRepository;
    private final FortuneTreeAttemptRepository attemptRepository;
    private final Cache<UUID, PlayerState> playerCache;
    private final Cache<UUID, AttemptState> attemptCache;
    private volatile boolean redisTemporarilyDisabled;
    private volatile long redisRetryAfterMillis;
    private volatile long redisLastWarnAtMillis;

    @Autowired
    public PersistentFortuneTreeStore(Logger logger,
                                     FortuneTreePlayerRepository playerRepository,
                                     FortuneTreeAttemptRepository attemptRepository,
                                     @Autowired(required = false) RedisService redis,
                                     @Autowired(required = false) CaffeineService caffeineService) {
        this.logger = logger;
        this.playerRepository = playerRepository;
        this.attemptRepository = attemptRepository;
        this.redis = redis;
        this.caffeineService = caffeineService;
        this.playerCache = createCache(PLAYER_JVM_CACHE_NAME);
        this.attemptCache = createCache(ATTEMPT_JVM_CACHE_NAME);
    }

    @PostConstruct
    public void logStatus() {
        logger.info("[FortuneTree] Persistence: redis=" + (isRedisConfigured() ? "enabled" : "missing/disabled")
                + ", mybatis(player)=" + (playerRepository != null && playerRepository.isAvailable())
                + ", mybatis(attempt)=" + (attemptRepository != null && attemptRepository.isAvailable()));
        logger.info("[FortuneTree] JVM cache: strategy=expire-after-access-ms"
                + ", ttlMs=" + JVM_CACHE_EXPIRE_AFTER_ACCESS_MILLIS
                + ", provider=" + (isCaffeineReady() ? "caffeine-starter" : "local-caffeine"));
    }

    @Override
    public PlayerState getOrCreatePlayer(UUID uuid) {
        if (uuid == null) {
            return new PlayerState(new UUID(0L, 0L));
        }
        return playerCache.get(uuid, this::loadPlayer);
    }

    @Override
    public AttemptState getOrCreateAttempt(UUID uuid, String cycleId) {
        if (uuid == null) {
            return new AttemptState(new UUID(0L, 0L), safeCycleId(cycleId));
        }
        String normalized = safeCycleId(cycleId);
        AttemptState existing = attemptCache.getIfPresent(uuid);
        if (existing != null && Objects.equals(existing.getCycleId(), normalized)) {
            return existing;
        }
        AttemptState loaded = loadAttempt(uuid, normalized);
        attemptCache.put(uuid, loaded);
        return loaded;
    }

    @Override
    public void savePlayer(PlayerState state) {
        savePlayer(state, null);
    }

    @Override
    public void savePlayer(PlayerState state, String playerName) {
        if (state == null || state.getUuid() == null) {
            return;
        }
        playerCache.put(state.getUuid(), state);
        if (playerRepository != null && playerRepository.isAvailable()) {
            playerRepository.upsert(state);
        }
        if (isRedisEnabled()) {
            savePlayerToRedis(state, playerName);
        }
    }

    @Override
    public void saveAttempt(AttemptState attempt) {
        if (attempt == null || attempt.getUuid() == null) {
            return;
        }
        attemptCache.put(attempt.getUuid(), attempt);
        if (attemptRepository != null && attemptRepository.isAvailable()) {
            attemptRepository.upsert(attempt);
        }
        if (isRedisEnabled()) {
            saveAttemptToRedis(attempt);
        }
    }

    @Override
    public boolean markCollected(AttemptState attempt, int index) {
        if (attempt == null) {
            return false;
        }
        if (index < 0 || index >= attempt.getBubbleCount()) {
            return false;
        }
        if (!isRedisEnabled()) {
            return attempt.markCollected(index);
        }
        String cycleId = safeCycleId(attempt.getCycleId());
        String key = collectKey(attempt.getUuid(), cycleId);
        try {
            boolean old = redis.setBit(key, index, true);
            redis.expire(key, ATTEMPT_TTL_SECONDS);
            if (!old) {
                attempt.markCollected(index);
                return true;
            }
            attempt.markCollected(index);
            return false;
        } catch (Exception ex) {
            onRedisFailure("markCollected", ex);
            return attempt.markCollected(index);
        }
    }

    @Override
    public void markAllCollected(AttemptState attempt) {
        if (attempt == null) {
            return;
        }
        attempt.markAllCollected();
        if (!isRedisEnabled()) {
            return;
        }
        String cycleId = safeCycleId(attempt.getCycleId());
        String key = collectKey(attempt.getUuid(), cycleId);
        try {
            int count = Math.max(0, attempt.getBubbleCount());
            for (int i = 0; i < count; i++) {
                redis.setBit(key, i, true);
            }
            redis.expire(key, ATTEMPT_TTL_SECONDS);
        } catch (Exception ex) {
            onRedisFailure("markAllCollected", ex);
        }
    }

    @Override
    public void evictPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        playerCache.invalidate(uuid);
        attemptCache.invalidate(uuid);
    }

    private PlayerState loadPlayer(UUID uuid) {
        PlayerState state = null;
        if (isRedisEnabled()) {
            state = loadPlayerFromRedis(uuid);
        }
        if (state == null && playerRepository != null && playerRepository.isAvailable()) {
            state = playerRepository.find(uuid);
        }
        if (state == null) {
            state = new PlayerState(uuid);
        }
        return state;
    }

    private AttemptState loadAttempt(UUID uuid, String cycleId) {
        AttemptState attempt = null;
        if (isRedisEnabled()) {
            attempt = loadAttemptFromRedis(uuid, cycleId);
        }
        if (attempt == null && attemptRepository != null && attemptRepository.isAvailable()) {
            attempt = attemptRepository.find(uuid, cycleId);
        }
        if (attempt == null) {
            attempt = new AttemptState(uuid, cycleId);
        }
        return attempt;
    }

    private PlayerState loadPlayerFromRedis(UUID uuid) {
        if (!isRedisEnabled() || uuid == null) {
            return null;
        }
        String key = playerKey(uuid);
        try {
            Map<String, String> map = redis.hgetAll(key);
            if (map == null || map.isEmpty()) {
                return null;
            }
            PlayerState state = new PlayerState(uuid);
            state.setLevel(parseInt(map.get("level"), 1, 1, 9999));
            state.setExp(parseInt(map.get("exp"), 0, 0, Integer.MAX_VALUE));
            state.setFreePicks(parseInt(map.get("free"), 0, 0, 9999));
            state.setFirstDone(parseBoolean(map.get("firstDone")));
            state.addTotalDeposit(parseLong(map.get("totalDeposit"), 0L));
            state.addTotalReward(parseLong(map.get("totalReward"), 0L));
            state.addCritCount(parseLong(map.get("critCount"), 0L));
            return state;
        } catch (Exception ex) {
            onRedisFailure("loadPlayer", ex);
            return null;
        }
    }

    private AttemptState loadAttemptFromRedis(UUID uuid, String cycleId) {
        if (!isRedisEnabled() || uuid == null) {
            return null;
        }
        String attemptKey = attemptKey(uuid, cycleId);
        try {
            Map<String, String> map = redis.hgetAll(attemptKey);
            if (map == null || map.isEmpty()) {
                return null;
            }
            AttemptState attempt = new AttemptState(uuid, cycleId);
            attempt.setUsedCount(parseInt(map.get("usedCount"), 0, 0, 9999));
            attempt.setStatus(parseStatus(map.get("status")));
            attempt.setLevel(parseInt(map.get("level"), 0, 0, 9999));
            attempt.setDeposit(parseInt(map.get("deposit"), 0, 0, Integer.MAX_VALUE));
            attempt.setRewardMax(parseInt(map.get("rewardMax"), 0, 0, Integer.MAX_VALUE));
            attempt.setSeed(parseLong(map.get("seed"), 0L));
            attempt.setBubbleCount(parseInt(map.get("bubbleCount"), 0, 0, 9999));
            attempt.setRerollCount(parseInt(map.get("rerollCount"), 0, 0, 9999));
            attempt.setCreatedAtMillis(parseLong(map.get("createdAt"), 0L));

            if (attempt.getBubbleCount() > 0) {
                String collectKey = collectKey(uuid, cycleId);
                for (int i = 0; i < attempt.getBubbleCount(); i++) {
                    if (redis.getBit(collectKey, i)) {
                        attempt.markCollected(i);
                    }
                }
            }
            return attempt;
        } catch (Exception ex) {
            onRedisFailure("loadAttempt", ex);
            return null;
        }
    }

    private void savePlayerToRedis(PlayerState state, String playerName) {
        if (state == null || state.getUuid() == null) {
            return;
        }
        try {
            Map<String, String> map = new HashMap<>();
            String normalizedPlayerName = safePlayerName(playerName);
            if (!normalizedPlayerName.isEmpty()) {
                map.put("name", normalizedPlayerName);
            }
            map.put("level", String.valueOf(state.getLevel()));
            map.put("exp", String.valueOf(state.getExp()));
            map.put("free", String.valueOf(state.getFreePicks()));
            map.put("firstDone", state.isFirstDone() ? "1" : "0");
            map.put("totalDeposit", String.valueOf(state.getTotalDeposit()));
            map.put("totalReward", String.valueOf(state.getTotalReward()));
            map.put("critCount", String.valueOf(state.getCritCount()));
            redis.hset(playerKey(state.getUuid()), map);
        } catch (Exception ex) {
            onRedisFailure("savePlayer", ex);
        }
    }

    private void saveAttemptToRedis(AttemptState attempt) {
        if (attempt == null || attempt.getUuid() == null) {
            return;
        }
        String cycleId = safeCycleId(attempt.getCycleId());
        if (cycleId.isBlank()) {
            return;
        }
        String key = attemptKey(attempt.getUuid(), cycleId);
        try {
            Map<String, String> map = new HashMap<>();
            map.put("cycleId", cycleId);
            map.put("usedCount", String.valueOf(attempt.getUsedCount()));
            map.put("status", attempt.getStatus() == null ? AttemptStatus.IDLE.name() : attempt.getStatus().name());
            map.put("level", String.valueOf(attempt.getLevel()));
            map.put("deposit", String.valueOf(attempt.getDeposit()));
            map.put("rewardMax", String.valueOf(attempt.getRewardMax()));
            map.put("seed", String.valueOf(attempt.getSeed()));
            map.put("bubbleCount", String.valueOf(attempt.getBubbleCount()));
            map.put("rerollCount", String.valueOf(attempt.getRerollCount()));
            map.put("createdAt", String.valueOf(attempt.getCreatedAtMillis()));
            redis.hset(key, map);
            redis.expire(key, ATTEMPT_TTL_SECONDS);

            String collectKey = collectKey(attempt.getUuid(), cycleId);
            if (attempt.collectedCount() <= 0) {
                redis.del(collectKey);
            } else {
                redis.expire(collectKey, ATTEMPT_TTL_SECONDS);
            }
        } catch (Exception ex) {
            onRedisFailure("saveAttempt", ex);
        }
    }

    private boolean isRedisEnabled() {
        if (!isRedisConfigured()) {
            return false;
        }
        if (!redisTemporarilyDisabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < redisRetryAfterMillis) {
            return false;
        }
        try {
            redis.exists(REDIS_HEALTH_KEY);
            redisTemporarilyDisabled = false;
            logger.info("[FortuneTree] Redis connectivity restored.");
            return true;
        } catch (Exception ex) {
            onRedisFailure("probe", ex);
            return false;
        }
    }

    private boolean isRedisConfigured() {
        return redis != null && redis.isEnabled();
    }

    private boolean isCaffeineReady() {
        return caffeineService != null && caffeineService.isEnabled();
    }

    private void onRedisFailure(String operation, Exception ex) {
        redisTemporarilyDisabled = true;
        redisRetryAfterMillis = System.currentTimeMillis() + REDIS_RETRY_COOLDOWN_MILLIS;
        long now = System.currentTimeMillis();
        if (now - redisLastWarnAtMillis < 30_000L) {
            return;
        }
        redisLastWarnAtMillis = now;
        String reason = ex == null ? "unknown" : ex.getClass().getSimpleName()
                + (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : (": " + ex.getMessage()));
        logger.warning("[FortuneTree] Redis unavailable during " + operation
                + ", fallback to MyBatis/Memory for 60s. reason=" + reason);
        if (ex != null) {
            logger.log(Level.FINE, "[FortuneTree] Redis failure detail.", ex);
        }
    }

    private String safeCycleId(String cycleId) {
        return cycleId == null ? "" : cycleId.trim();
    }

    private String safePlayerName(String playerName) {
        if (playerName == null) {
            return "";
        }
        return playerName.trim();
    }

    private String playerKey(UUID uuid) {
        return String.format(PLAYER_KEY, uuid);
    }

    private String attemptKey(UUID uuid, String cycleId) {
        return String.format(ATTEMPT_KEY, uuid, cycleId);
    }

    private String collectKey(UUID uuid, String cycleId) {
        return String.format(COLLECT_KEY, uuid, cycleId);
    }

    private int parseInt(String value, int defaultValue, int min, int max) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(value.trim());
            if (v < min) {
                return min;
            }
            if (v > max) {
                return max;
            }
            return v;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
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

    private <K, V> Cache<K, V> createCache(String cacheName) {
        if (isCaffeineReady()) {
            try {
                return caffeineService.<K, V>typedBuilder(cacheName)
                        .expireAfterAccess(Duration.ofMillis(JVM_CACHE_EXPIRE_AFTER_ACCESS_MILLIS))
                        .build();
            } catch (Exception ex) {
                logger.warning("[FortuneTree] Failed to build JVM cache via caffeine starter for "
                        + cacheName + ", fallback to local cache. reason=" + ex.getMessage());
            }
        }
        return Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMillis(JVM_CACHE_EXPIRE_AFTER_ACCESS_MILLIS))
                .build();
    }
}
