package com.cuzz.rookiefortunetree.service.economy;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

@Component
public final class PlayerPointsEconomyGateway {
    private final Logger logger;
    private final Object bindingLock = new Object();
    private volatile Plugin cachedPlugin;
    private volatile ApiBinding cachedBinding;
    private volatile long nextWarnAtMillis;

    @Autowired
    public PlayerPointsEconomyGateway(Logger logger) {
        this.logger = logger;
    }

    public boolean take(Player player, int amount, String reason) {
        if (player == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        ApiBinding binding = resolveBinding(true);
        if (binding == null) {
            return false;
        }
        try {
            if (binding.take(player, amount)) {
                return true;
            }
        } catch (Exception ex) {
            warnThrottled("[FortuneTree] PlayerPoints API take threw exception: " + ex.getClass().getSimpleName());
        }

        int balance = safeLook(binding, player);
        if (binding.forceOffset(player.getUniqueId(), -amount)) {
            warnThrottled("[FortuneTree] PlayerPoints API take returned false, used DataManager fallback. "
                    + "player=" + player.getName() + ", amount=" + amount + ", balance=" + balance);
            return true;
        }

        warnThrottled("[FortuneTree] PlayerPoints take failed. player=" + player.getName()
                + ", amount=" + amount + ", balance=" + balance);
        return false;
    }

    public boolean give(Player player, int amount, String reason) {
        if (player == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        ApiBinding binding = resolveBinding(true);
        if (binding == null) {
            return false;
        }
        try {
            if (binding.give(player, amount)) {
                return true;
            }
        } catch (Exception ex) {
            warnThrottled("[FortuneTree] PlayerPoints API give threw exception: " + ex.getClass().getSimpleName());
        }

        if (binding.forceOffset(player.getUniqueId(), amount)) {
            warnThrottled("[FortuneTree] PlayerPoints API give returned false, used DataManager fallback. "
                    + "player=" + player.getName() + ", amount=" + amount);
            return true;
        }

        int balance = safeLook(binding, player);
        warnThrottled("[FortuneTree] PlayerPoints give failed. player=" + player.getName()
                + ", amount=" + amount + ", balance=" + balance);
        return false;
    }

    public boolean isAvailable() {
        return resolveBinding(false) != null;
    }

    private ApiBinding resolveBinding(boolean warnWhenMissing) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (plugin == null || !plugin.isEnabled()) {
            cachedPlugin = null;
            cachedBinding = null;
            if (warnWhenMissing) {
                warnThrottled("[FortuneTree] economy.type=playerpoints but PlayerPoints plugin is unavailable.");
            }
            return null;
        }

        ApiBinding existing = cachedBinding;
        if (existing != null && cachedPlugin == plugin) {
            return existing;
        }

        synchronized (bindingLock) {
            existing = cachedBinding;
            if (existing != null && cachedPlugin == plugin) {
                return existing;
            }
            ApiBinding created = createBinding(plugin);
            cachedPlugin = plugin;
            cachedBinding = created;
            if (created == null && warnWhenMissing) {
                warnThrottled("[FortuneTree] Failed to bind PlayerPoints API, check plugin version compatibility.");
            }
            return created;
        }
    }

    private ApiBinding createBinding(Plugin plugin) {
        try {
            Method getApi = plugin.getClass().getMethod("getAPI");
            Object api = getApi.invoke(plugin);
            if (api == null) {
                return null;
            }

            Method take = findTransferMethod(api.getClass(), "take");
            Method give = findTransferMethod(api.getClass(), "give");
            if (take == null || give == null) {
                return null;
            }
            Method look = findLookMethod(api.getClass());
            DataManagerBinding dataManagerBinding = createDataManagerBinding(plugin);
            return new ApiBinding(api, take, give, look, dataManagerBinding);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DataManagerBinding createDataManagerBinding(Plugin plugin) {
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> managerType = Class.forName("org.black_ixx.playerpoints.manager.DataManager", true, loader);
            Method getManager = plugin.getClass().getMethod("getManager", Class.class);
            Object manager = getManager.invoke(plugin, managerType);
            if (manager == null) {
                return null;
            }
            Method offset = managerType.getMethod("offsetPoints", UUID.class, int.class);
            Method look = managerType.getMethod("getEffectivePoints", UUID.class);
            return new DataManagerBinding(manager, offset, look);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Method findTransferMethod(Class<?> apiType, String methodName) {
        Method best = null;
        int bestScore = -1;
        for (Method method : apiType.getMethods()) {
            if (!methodName.equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?> playerType = method.getParameterTypes()[0];
            Class<?> amountType = method.getParameterTypes()[1];
            if (!isAmountTypeSupported(amountType)) {
                continue;
            }
            int score = playerTypeScore(playerType);
            if (score > bestScore) {
                bestScore = score;
                best = method;
            }
        }
        return best;
    }

    private Method findLookMethod(Class<?> apiType) {
        for (Method method : apiType.getMethods()) {
            if (!"look".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> playerType = method.getParameterTypes()[0];
            if (playerType == UUID.class
                    || playerType.isAssignableFrom(Player.class)
                    || playerType.isAssignableFrom(OfflinePlayer.class)
                    || playerType == String.class) {
                return method;
            }
        }
        return null;
    }

    private boolean isAmountTypeSupported(Class<?> amountType) {
        return amountType == int.class
                || amountType == Integer.class
                || amountType == long.class
                || amountType == Long.class;
    }

    private int playerTypeScore(Class<?> playerType) {
        if (playerType == UUID.class) {
            return 30;
        }
        if (playerType.isAssignableFrom(Player.class) || playerType.isAssignableFrom(OfflinePlayer.class)) {
            return 20;
        }
        if (playerType == String.class) {
            return 10;
        }
        return -1;
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now < nextWarnAtMillis) {
            return;
        }
        nextWarnAtMillis = now + 30_000L;
        logger.warning(message);
    }

    private int safeLook(ApiBinding binding, Player player) {
        if (binding == null || player == null) {
            return -1;
        }
        try {
            return binding.look(player);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static Object convertPlayerArg(Class<?> playerType, Player player) {
        if (playerType == UUID.class) {
            return player.getUniqueId();
        }
        if (playerType.isAssignableFrom(Player.class) || playerType.isAssignableFrom(OfflinePlayer.class)) {
            return player;
        }
        if (playerType == String.class) {
            return player.getName();
        }
        return null;
    }

    private static boolean toSuccess(Class<?> returnType, Object result) {
        if (returnType == void.class) {
            return true;
        }
        if (result instanceof Boolean value) {
            return value;
        }
        if (result instanceof Number value) {
            return value.longValue() >= 0L;
        }
        return result != null;
    }

    private record ApiBinding(
            Object api,
            Method takeMethod,
            Method giveMethod,
            Method lookMethod,
            DataManagerBinding dataManagerBinding
    ) {
        boolean take(Player player, int amount) throws Exception {
            return invokeTransfer(takeMethod, player, amount);
        }

        boolean give(Player player, int amount) throws Exception {
            return invokeTransfer(giveMethod, player, amount);
        }

        int look(Player player) throws Exception {
            if (lookMethod == null || player == null) {
                if (dataManagerBinding != null && player != null) {
                    return dataManagerBinding.look(player.getUniqueId());
                }
                return -1;
            }
            Class<?>[] parameterTypes = lookMethod.getParameterTypes();
            Object playerArg = convertPlayerArg(parameterTypes[0], player);
            if (playerArg == null) {
                return dataManagerBinding == null ? -1 : dataManagerBinding.look(player.getUniqueId());
            }
            Object result = lookMethod.invoke(api, playerArg);
            if (result instanceof Number number) {
                return number.intValue();
            }
            return -1;
        }

        boolean forceOffset(UUID playerId, int delta) {
            return dataManagerBinding != null && dataManagerBinding.offset(playerId, delta);
        }

        private boolean invokeTransfer(Method method, Player player, int amount) throws Exception {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object playerArg = convertPlayerArg(parameterTypes[0], player);
            if (playerArg == null) {
                return false;
            }
            Object amountArg = (parameterTypes[1] == long.class || parameterTypes[1] == Long.class)
                    ? (long) amount
                    : amount;
            Object result = method.invoke(api, playerArg, amountArg);
            return toSuccess(method.getReturnType(), result);
        }
    }

    private record DataManagerBinding(Object manager, Method offsetMethod, Method lookMethod) {
        boolean offset(UUID playerId, int delta) {
            if (playerId == null || offsetMethod == null || manager == null) {
                return false;
            }
            try {
                Object result = offsetMethod.invoke(manager, playerId, delta);
                if (result instanceof Boolean ok) {
                    return ok;
                }
                return result != null;
            } catch (Exception ignored) {
                return false;
            }
        }

        int look(UUID playerId) {
            if (playerId == null || lookMethod == null || manager == null) {
                return -1;
            }
            try {
                Object result = lookMethod.invoke(manager, playerId);
                if (result instanceof Number number) {
                    return number.intValue();
                }
                return -1;
            } catch (Exception ignored) {
                return -1;
            }
        }
    }
}
