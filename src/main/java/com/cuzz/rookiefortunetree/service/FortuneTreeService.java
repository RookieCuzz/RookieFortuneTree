package com.cuzz.rookiefortunetree.service;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Service;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.menu.FortuneTreeMenu;
import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.AttemptStatus;
import com.cuzz.rookiefortunetree.model.Bubble;
import com.cuzz.rookiefortunetree.model.BubbleType;
import com.cuzz.rookiefortunetree.model.LevelConfig;
import com.cuzz.rookiefortunetree.model.PlayerState;
import com.cuzz.rookiefortunetree.service.economy.EconomyGateway;
import com.cuzz.rookiefortunetree.storage.FortuneTreeStore;
import com.cuzz.rookiefortunetree.util.TextUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import nl.odalitadevelopments.menus.OdalitaMenus;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public final class FortuneTreeService {
    private final JavaPlugin plugin;
    private final FortuneTreeConfig config;
    private final ResetClockService resetClockService;
    private final RewardGenerator rewardGenerator;
    private final FortuneTreeStore store;
    private final EconomyGateway economy;
    private final FortuneTreeMenu menu;
    private final Logger logger;
    private final Object menusLock = new Object();
    private volatile OdalitaMenus menus;

    @Autowired
    public FortuneTreeService(JavaPlugin plugin,
                              FortuneTreeConfig config,
                              ResetClockService resetClockService,
                              RewardGenerator rewardGenerator,
                              FortuneTreeStore store,
                              EconomyGateway economy,
                              FortuneTreeMenu menu,
                              Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.resetClockService = resetClockService;
        this.rewardGenerator = rewardGenerator;
        this.store = store;
        this.economy = economy;
        this.menu = menu;
        this.logger = logger;
    }

    public void openMenu(Player player, boolean allowReroll) {
        if (player == null) {
            return;
        }
        OdalitaMenus menus = resolveMenus();
        if (menus == null) {
            player.sendMessage(ChatColor.RED + "Menu system is not available right now.");
            return;
        }
        CycleInfo cycle = resetClockService.now();
        AttemptState attempt = store.getOrCreateAttempt(player.getUniqueId(), cycle.cycleId());
        if (allowReroll) {
            maybeAutoReroll(player, attempt);
        }
        menus.openMenu(menu, player);
        player.playSound(player.getLocation(), config.openSound(), 1.0f, 1.0f);
    }

    public void water(Player player, Integer levelOverride) {
        if (player == null) {
            return;
        }
        CycleInfo cycle = resetClockService.now();
        AttemptState attempt = store.getOrCreateAttempt(player.getUniqueId(), cycle.cycleId());
        if (!canStartWaterAttempt(player, attempt)) {
            return;
        }

        PlayerState state = store.getOrCreatePlayer(player.getUniqueId());
        LevelConfig level = resolveLevelForWater(state, levelOverride, player);
        if (level == null) {
            return;
        }
        WaterPlan plan = createWaterPlan(player, state, level);
        if (plan == null) {
            return;
        }
        if (!takeDeposit(player, plan.deposit())) {
            return;
        }

        applyNewbieAndFreePick(state, level, plan.deposit());
        applyExpAndLevelUp(state, plan.expGain());
        state.addTotalDeposit(plan.deposit());

        attempt.incrementUsedCount();
        attempt.startNewAttempt(level.level(), plan.deposit(), level.rewardMax(), plan.seed(), plan.bubbleCount());
        store.savePlayer(state, player.getName());
        store.saveAttempt(attempt);
        send(player, config.msgWaterDone());
        player.playSound(player.getLocation(), config.waterSound(), 1.0f, 1.0f);
        logEvent("water", player, Map.of(
                "cycle", cycle.cycleId(),
                "level", String.valueOf(level.level()),
                "deposit", String.valueOf(plan.deposit()),
                "expGain", String.valueOf(plan.expGain()),
                "bubbleCount", String.valueOf(plan.bubbleCount())
        ));

        openMenu(player, false);
    }

    public void collectBubble(Player player, int bubbleIndex) {
        if (player == null) {
            return;
        }
        CycleInfo cycle = resetClockService.now();
        AttemptState attempt = store.getOrCreateAttempt(player.getUniqueId(), cycle.cycleId());
        if (attempt.getStatus() != AttemptStatus.PENDING) {
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return;
        }
        if (bubbleIndex < 0 || bubbleIndex >= attempt.getBubbleCount()) {
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return;
        }
        LevelConfig level = resolveAttemptLevel(attempt);
        if (level == null) {
            failInvalidConfig(player, "missing level config for attempt.level=" + attempt.getLevel());
            return;
        }

        GenerationResult result = generateOrNotify(player, level, attempt);
        if (result == null) {
            return;
        }
        Bubble bubble = result.bubbles().get(bubbleIndex);
        if (!store.markCollected(attempt, bubbleIndex)) {
            return;
        }
        if (!economy.give(player, bubble.amount(), "fortune_tree_reward")) {
            rollbackCollected(attempt, List.of(bubbleIndex));
            send(player, ChatColor.RED + "Reward delivery failed, please retry.");
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return;
        }
        PlayerState state = store.getOrCreatePlayer(player.getUniqueId());
        state.addTotalReward(bubble.amount());
        if (bubble.type() == BubbleType.CRIT) {
            state.addCritCount(1);
        }
        sendBubbleCollectActionBar(player, bubble);

        if (attempt.isAllCollected()) {
            attempt.setStatus(AttemptStatus.COLLECTED);
            send(player, TextUtil.format(config.msgCollectDone(), Map.of(
                    "amount", String.valueOf(result.rewardTotal()),
                    "deposit", String.valueOf(attempt.getDeposit())
            )));
            logEvent("collect_done", player, Map.of(
                    "cycle", cycle.cycleId(),
                    "reward", String.valueOf(result.rewardTotal()),
                    "deposit", String.valueOf(attempt.getDeposit()),
                    "reroll", String.valueOf(attempt.getRerollCount())
            ));
        }
        store.savePlayer(state, player.getName());
        store.saveAttempt(attempt);
        player.playSound(player.getLocation(), config.collectSound(), 1.0f, 1.0f);
        openMenu(player, false);
    }

    public void collectAll(Player player) {
        if (player == null) {
            return;
        }
        CycleInfo cycle = resetClockService.now();
        AttemptState attempt = store.getOrCreateAttempt(player.getUniqueId(), cycle.cycleId());
        if (attempt.getStatus() != AttemptStatus.PENDING) {
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return;
        }
        LevelConfig level = resolveAttemptLevel(attempt);
        if (level == null) {
            failInvalidConfig(player, "missing level config for attempt.level=" + attempt.getLevel());
            return;
        }
        GenerationResult result = generateOrNotify(player, level, attempt);
        if (result == null) {
            return;
        }
        CollectAllProgress progress = collectRemainingBubbles(attempt, result);
        int gained = progress.gained();
        int gainedCrit = progress.gainedCrit();
        List<Integer> newlyCollected = progress.newlyCollected();
        if (gained <= 0) {
            if (attempt.isAllCollected() && attempt.getStatus() != AttemptStatus.COLLECTED) {
                attempt.setStatus(AttemptStatus.COLLECTED);
                store.saveAttempt(attempt);
            }
            return;
        }
        if (!economy.give(player, gained, "fortune_tree_reward_all")) {
            rollbackCollected(attempt, newlyCollected);
            send(player, ChatColor.RED + "Reward delivery failed, please retry.");
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return;
        }
        PlayerState state = store.getOrCreatePlayer(player.getUniqueId());
        state.addTotalReward(gained);
        if (gainedCrit > 0) {
            state.addCritCount(gainedCrit);
        }
        attempt.setStatus(AttemptStatus.COLLECTED);
        int todayTotal = computeCollectedReward(result, attempt);
        send(player, TextUtil.colorize("&a今日累计收获 &6" + todayTotal + "&a 元宝（本次 +&e" + gained + "&a）"));
        player.playSound(player.getLocation(), config.collectSound(), 1.0f, 1.0f);
        logEvent("collect_all", player, Map.of(
                "cycle", cycle.cycleId(),
                "reward", String.valueOf(gained),
                "todayTotal", String.valueOf(todayTotal),
                "deposit", String.valueOf(attempt.getDeposit())
        ));
        store.savePlayer(state, player.getName());
        store.saveAttempt(attempt);
        openMenu(player, false);
    }

    public void debug(Player player) {
        if (player == null) {
            return;
        }
        CycleInfo cycle = resetClockService.now();
        PlayerState state = store.getOrCreatePlayer(player.getUniqueId());
        AttemptState attempt = store.getOrCreateAttempt(player.getUniqueId(), cycle.cycleId());
        player.sendMessage(TextUtil.colorize("&7[FortuneTree] cycle=" + cycle.cycleId()
                + " resetIn=" + resetClockService.format(cycle.untilNextReset())));
        player.sendMessage(TextUtil.colorize("&7[FortuneTree] level=" + state.getLevel()
                + " exp=" + state.getExp()
                + " free=" + state.getFreePicks()
                + " firstDone=" + state.isFirstDone()));
        player.sendMessage(TextUtil.colorize("&7[FortuneTree] attempt used=" + attempt.getUsedCount()
                + " status=" + attempt.getStatus()
                + " deposit=" + attempt.getDeposit()
                + " seed=" + attempt.getSeed()
                + " bubbles=" + attempt.getBubbleCount()
                + " collected=" + attempt.collectedCount()
                + " reroll=" + attempt.getRerollCount()));
    }

    private int resolveEffectiveLevel(PlayerState state, Integer levelOverride, Player player) {
        if (levelOverride == null) {
            return state.getLevel();
        }
        if (player != null && player.hasPermission("dailywatertree.admin")) {
            return Math.max(1, levelOverride);
        }
        return state.getLevel();
    }

    private int resolveDepositCost(PlayerState state, LevelConfig level) {
        if (state == null || level == null) {
            return 0;
        }
        if (!state.isFirstDone()) {
            return Math.max(0, config.newbieFirstPickCost());
        }
        if (state.getFreePicks() > 0) {
            return 0;
        }
        return Math.max(0, level.deposit());
    }

    private void applyNewbieAndFreePick(PlayerState state, LevelConfig level, int deposit) {
        if (state == null) {
            return;
        }
        if (!state.isFirstDone()) {
            state.setFirstDone(true);
            state.setFreePicks(config.newbieFreePicksAfterFirst());
            return;
        }
        if (state.getFreePicks() > 0 && deposit == 0 && (level == null || level.deposit() > 0)) {
            state.setFreePicks(state.getFreePicks() - 1);
        }
    }

    private void applyExpAndLevelUp(PlayerState state, int expGained) {
        if (state == null || expGained <= 0) {
            return;
        }
        state.setExp(state.getExp() + expGained);
        // Consume exp-to-next repeatedly to support multi-level-up in one water action.
        while (true) {
            LevelConfig current = config.levelById(state.getLevel());
            if (current == null) {
                return;
            }
            int need = current.expToNext();
            if (need <= 0) {
                return;
            }
            if (state.getExp() < need) {
                return;
            }
            LevelConfig next = config.levelById(current.level() + 1);
            if (next == null || Objects.equals(next.level(), current.level())) {
                return;
            }
            state.setExp(state.getExp() - need);
            state.setLevel(next.level());
        }
    }

    static int computeExpGain(LevelConfig level, int chargedDeposit) {
        int paid = Math.max(0, chargedDeposit);
        int base = level == null ? 0 : Math.max(0, level.deposit());
        int gain = Math.max(paid, base);
        return Math.max(1, gain);
    }

    private boolean canStartWaterAttempt(Player player, AttemptState attempt) {
        if (attempt.getStatus() == AttemptStatus.PENDING) {
            send(player, config.msgAlreadyPending());
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return false;
        }
        int limit = config.dailyLimit();
        if (attempt.getUsedCount() >= limit) {
            send(player, config.msgNoMoreTimes());
            player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
            return false;
        }
        return true;
    }

    private LevelConfig resolveLevelForWater(PlayerState state, Integer levelOverride, Player player) {
        int effectiveLevel = resolveEffectiveLevel(state, levelOverride, player);
        LevelConfig level = config.levelById(effectiveLevel);
        if (level != null) {
            return level;
        }
        failInvalidConfig(player, "missing level config for level=" + effectiveLevel);
        return null;
    }

    private WaterPlan createWaterPlan(Player player, PlayerState state, LevelConfig level) {
        int deposit = resolveDepositCost(state, level);
        int profitMin = Math.max(1, config.profitMin());
        int bubbleSlots = Math.max(1, config.bubbleSlots().size());
        long seed = ThreadLocalRandom.current().nextLong();
        int bubbleCount = rewardGenerator.pickBubbleCount(level, seed, bubbleSlots);
        int minTotal = Math.max(deposit + profitMin, bubbleCount);
        if (minTotal > level.rewardMax()) {
            failInvalidConfig(player, "rewardMax too small (level=" + level.level()
                    + ", rewardMax=" + level.rewardMax()
                    + ", minTotal=" + minTotal
                    + ", deposit=" + deposit
                    + ", profitMin=" + profitMin
                    + ", bubbleCount=" + bubbleCount + ")");
            return null;
        }
        int expGain = computeExpGain(level, deposit);
        return new WaterPlan(deposit, bubbleCount, seed, expGain);
    }

    private boolean takeDeposit(Player player, int deposit) {
        if (economy.take(player, deposit, "fortune_tree_deposit")) {
            return true;
        }
        send(player, ChatColor.RED + "Failed to take deposit: " + deposit
                + ". Please check balance/economy config.");
        player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
        return false;
    }

    private GenerationResult generateOrNotify(Player player, LevelConfig level, AttemptState attempt) {
        try {
            return rewardGenerator.generate(level, attempt.getDeposit(), attempt.getSeed(), attempt.getBubbleCount());
        } catch (IllegalArgumentException ex) {
            failInvalidConfig(player, ex.getMessage());
            return null;
        }
    }

    private CollectAllProgress collectRemainingBubbles(AttemptState attempt, GenerationResult result) {
        int gained = 0;
        int gainedCrit = 0;
        List<Integer> newlyCollected = new ArrayList<>();
        // Count only bubbles that are newly collected in this operation.
        for (Bubble bubble : result.bubbles()) {
            int idx = bubble.index();
            if (!store.markCollected(attempt, idx)) {
                continue;
            }
            newlyCollected.add(idx);
            gained += bubble.amount();
            if (bubble.type() == BubbleType.CRIT) {
                gainedCrit++;
            }
        }
        return new CollectAllProgress(gained, gainedCrit, newlyCollected);
    }

    private void maybeAutoReroll(Player player, AttemptState attempt) {
        if (attempt == null) {
            return;
        }
        if (!config.rerollEnabled()) {
            return;
        }
        if (attempt.getStatus() != AttemptStatus.PENDING) {
            return;
        }
        if (config.lockAfterFirstCollect() && attempt.collectedCount() > 0) {
            return;
        }
        int max = config.rerollMaxTimesPerWater();
        if (max > 0 && attempt.getRerollCount() >= max) {
            return;
        }
        LevelConfig level = resolveAttemptLevel(attempt);
        if (level == null) {
            return;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        int bubbleSlots = Math.max(1, config.bubbleSlots().size());
        int bubbleCount = rewardGenerator.pickBubbleCount(level, seed, bubbleSlots);
        attempt.reroll(seed, bubbleCount);
        store.saveAttempt(attempt);
        send(player, config.msgRerollHint());
        logEvent("reroll", player, Map.of(
                "cycle", attempt.getCycleId(),
                "reroll", String.valueOf(attempt.getRerollCount())
        ));
    }

    private LevelConfig resolveAttemptLevel(AttemptState attempt) {
        if (attempt == null) {
            return null;
        }
        LevelConfig base = config.levelById(attempt.getLevel());
        if (base == null) {
            return null;
        }
        if (attempt.getRewardMax() == base.rewardMax()) {
            return base;
        }
        return new LevelConfig(
                base.level(),
                base.name(),
                base.deposit(),
                attempt.getRewardMax(),
                base.expToNext(),
                base.bubbleMin(),
                base.bubbleMax(),
                base.critChance(),
                base.critShareMin(),
                base.critShareMax()
        );
    }

    private void send(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(TextUtil.colorize(message));
    }

    private void sendBubbleCollectActionBar(Player player, Bubble bubble) {
        if (player == null || bubble == null) {
            return;
        }
        String typeLabel = bubble.type() == BubbleType.CRIT ? "&d暴击" : "&a普通";
        String message = "&7收取气泡 " + typeLabel + " &7+&6" + bubble.amount();
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(TextUtil.colorize(message))
        );
    }

    private void failInvalidConfig(Player player, String reason) {
        if (player == null) {
            return;
        }
        send(player, TextUtil.format(config.msgInvalidConfig(), Map.of("reason", reason == null ? "" : reason)));
        player.playSound(player.getLocation(), config.failSound(), 1.0f, 1.0f);
        logger.warning("[FortuneTree] Invalid config: " + reason);
    }

    private void rollbackCollected(AttemptState attempt, List<Integer> bubbleIndexes) {
        if (attempt == null || bubbleIndexes == null || bubbleIndexes.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Integer bubbleIndex : bubbleIndexes) {
            if (bubbleIndex == null) {
                continue;
            }
            if (attempt.unmarkCollected(bubbleIndex)) {
                changed = true;
            }
        }
        if (attempt.getStatus() == AttemptStatus.COLLECTED && !attempt.isAllCollected()) {
            attempt.setStatus(AttemptStatus.PENDING);
            changed = true;
        }
        if (changed) {
            store.saveAttempt(attempt);
        }
    }

    private int computeCollectedReward(GenerationResult result, AttemptState attempt) {
        if (result == null || result.bubbles() == null || attempt == null) {
            return 0;
        }
        int total = 0;
        for (Bubble bubble : result.bubbles()) {
            if (bubble != null && attempt.isCollected(bubble.index())) {
                total += Math.max(0, bubble.amount());
            }
        }
        return total;
    }

    private void logEvent(String event, Player player, Map<String, String> labels) {
        if (player == null) {
            return;
        }
        logger.info("[FortuneTree] " + event + " player=" + player.getName()
                + " labels=" + (labels == null ? Map.of() : labels));
    }

    private OdalitaMenus resolveMenus() {
        OdalitaMenus current = menus;
        if (current != null) {
            return current;
        }
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        synchronized (menusLock) {
            if (menus != null) {
                return menus;
            }
            try {
                menus = OdalitaMenus.createInstance(plugin);
                logger.info("[FortuneTree] Initialized OdalitaMenus via createInstance.");
                return menus;
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "[FortuneTree] Failed to initialize OdalitaMenus instance.", ex);
                return null;
            }
        }
    }

    private record WaterPlan(int deposit, int bubbleCount, long seed, int expGain) {
    }

    private record CollectAllProgress(int gained, int gainedCrit, List<Integer> newlyCollected) {
    }
}
