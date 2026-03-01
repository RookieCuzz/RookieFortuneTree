package com.cuzz.rookiefortunetree.menu;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.config.MenuIconConfig;
import com.cuzz.rookiefortunetree.config.MenuIconDefinition;
import com.cuzz.rookiefortunetree.model.AttemptState;
import com.cuzz.rookiefortunetree.model.AttemptStatus;
import com.cuzz.rookiefortunetree.model.Bubble;
import com.cuzz.rookiefortunetree.model.LevelConfig;
import com.cuzz.rookiefortunetree.model.PlayerState;
import com.cuzz.rookiefortunetree.service.CycleInfo;
import com.cuzz.rookiefortunetree.service.GenerationResult;
import com.cuzz.rookiefortunetree.service.ResetClockService;
import com.cuzz.rookiefortunetree.service.RewardGenerator;
import com.cuzz.rookiefortunetree.storage.FortuneTreeStore;
import com.cuzz.rookiefortunetree.util.MenuTitleUtil;
import com.cuzz.rookiefortunetree.util.TextUtil;
import com.cuzz.rookiefortunetree.wrapper.FortuneTreeActionDispatcher;
import nl.odalitadevelopments.menus.annotations.Menu;
import nl.odalitadevelopments.menus.contents.MenuContents;
import nl.odalitadevelopments.menus.items.ClickableItem;
import nl.odalitadevelopments.menus.items.DisplayItem;
import nl.odalitadevelopments.menus.items.buttons.CloseItem;
import nl.odalitadevelopments.menus.menu.providers.PlayerMenuProvider;
import nl.odalitadevelopments.menus.menu.type.MenuType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Component
@Menu(id = "fortune_tree_menu", title = " ", type = MenuType.CHEST_6_ROW)
public final class FortuneTreeMenu implements PlayerMenuProvider {
    private final FortuneTreeConfig config;
    private final MenuIconConfig iconConfig;
    private final FortuneTreeStore store;
    private final ResetClockService resetClockService;
    private final RewardGenerator rewardGenerator;
    private final FortuneTreeActionDispatcher dispatcher;

    private static final int SIZE = 54;
    private static final int TREE_SLOT = 22;

    @Autowired
    public FortuneTreeMenu(FortuneTreeConfig config,
                           MenuIconConfig iconConfig,
                           FortuneTreeStore store,
                           ResetClockService resetClockService,
                           RewardGenerator rewardGenerator,
                           FortuneTreeActionDispatcher dispatcher) {
        this.config = config;
        this.iconConfig = iconConfig;
        this.store = store;
        this.resetClockService = resetClockService;
        this.rewardGenerator = rewardGenerator;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onLoad(Player player, MenuContents contents) {
        contents.setTitle(MenuTitleUtil.toJson(config.menuTitle()));
        render(player, contents);
    }

    private void render(Player player, MenuContents contents) {
        CycleInfo cycle = resetClockService.now();
        PlayerState playerState = store.getOrCreatePlayer(player.getUniqueId());
        AttemptState attempt = store.getOrCreateAttempt(player.getUniqueId(), cycle.cycleId());

        ItemStack border = buildIcon(iconConfig.get("BORDER"), Map.of(), Material.GRAY_STAINED_GLASS_PANE, "&7");
        for (int slot = 0; slot < SIZE; slot++) {
            contents.set(slot, DisplayItem.of(border));
        }

        renderTree(contents, playerState, attempt, cycle);
        renderButtons(player, contents, playerState, attempt, cycle);
        renderBubbles(player, contents, playerState, attempt);
    }

    private void renderTree(MenuContents contents, PlayerState state, AttemptState attempt, CycleInfo cycle) {
        LevelConfig level = config.levelById(state.getLevel());
        LevelConfig attemptLevel = attempt.getStatus() == AttemptStatus.PENDING ? config.levelById(attempt.getLevel()) : level;
        int depositPreview = attempt.getStatus() == AttemptStatus.PENDING
                ? attempt.getDeposit()
                : previewDeposit(state, level);
        int rewardMaxPreview = attempt.getStatus() == AttemptStatus.PENDING
                ? attempt.getRewardMax()
                : (attemptLevel == null ? 0 : attemptLevel.rewardMax());

        int remaining = Math.max(0, config.dailyLimit() - attempt.getUsedCount());
        String status = resolveStatus(attempt, remaining);
        String levelName = attemptLevel == null ? (level == null ? "LV?" : level.safeName()) : attemptLevel.safeName();
        String expToNext = level == null || level.expToNext() <= 0 ? "-" : String.valueOf(level.expToNext());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("levelName", levelName);
        placeholders.put("level", String.valueOf(state.getLevel()));
        placeholders.put("exp", String.valueOf(state.getExp()));
        placeholders.put("expToNext", expToNext);
        placeholders.put("status", status);
        placeholders.put("remaining", String.valueOf(remaining));
        placeholders.put("dailyLimit", String.valueOf(config.dailyLimit()));
        placeholders.put("resetIn", resetClockService.format(cycle.untilNextReset()));
        placeholders.put("deposit", String.valueOf(depositPreview));
        placeholders.put("rewardMax", String.valueOf(rewardMaxPreview));
        placeholders.put("free", String.valueOf(state.getFreePicks()));

        ItemStack item = buildIcon(iconConfig.get("TREE"), placeholders, Material.OAK_SAPLING, "&6元宝树");
        contents.set(TREE_SLOT, DisplayItem.of(item));
    }

    private void renderButtons(Player player, MenuContents contents, PlayerState state, AttemptState attempt, CycleInfo cycle) {
        int remaining = Math.max(0, config.dailyLimit() - attempt.getUsedCount());
        boolean canWater = attempt.getStatus() != AttemptStatus.PENDING && remaining > 0;

        LevelConfig level = config.levelById(state.getLevel());
        int depositPreview = previewDeposit(state, level);
        Map<String, String> waterPlaceholders = Map.of(
                "deposit", String.valueOf(depositPreview),
                "resetIn", resetClockService.format(cycle.untilNextReset())
        );

        ItemStack waterItem = canWater
                ? buildIcon(iconConfig.get("WATER_READY"), waterPlaceholders, Material.WATER_BUCKET, "&a浇水/摇树")
                : buildIcon(iconConfig.get("WATER_DISABLED"), waterPlaceholders, Material.BUCKET, "&c今日已浇水");
        if (canWater) {
            contents.set(config.waterSlot(), ClickableItem.of(waterItem, e -> dispatcher.water(player, null)));
        } else {
            contents.set(config.waterSlot(), DisplayItem.of(waterItem));
        }

        boolean canCollect = attempt.getStatus() == AttemptStatus.PENDING && !attempt.isAllCollected();
        ItemStack collectAll = buildIcon(iconConfig.get("COLLECT_ALL"), Map.of(), Material.HOPPER, "&e一键收取");
        if (canCollect) {
            contents.set(config.collectAllSlot(), ClickableItem.of(collectAll, e -> dispatcher.collectAll(player)));
        } else {
            contents.set(config.collectAllSlot(), DisplayItem.of(collectAll));
        }

        Map<String, String> rulesPlaceholders = Map.of(
                "firstPickCost", String.valueOf(config.newbieFirstPickCost()),
                "freePicksAfterFirst", String.valueOf(config.newbieFreePicksAfterFirst()),
                "dailyLimit", String.valueOf(config.dailyLimit())
        );
        ItemStack rules = buildIcon(iconConfig.get("RULES"), rulesPlaceholders, Material.BOOK, "&e玩法说明");
        contents.set(config.rulesSlot(), ClickableItem.of(rules, e -> sendRules(player)));

        ItemStack close = buildIcon(iconConfig.get("CLOSE"), Map.of(), Material.BARRIER, "&c关闭");
        contents.set(config.closeSlot(), CloseItem.of(close));
    }

    private void renderBubbles(Player player, MenuContents contents, PlayerState state, AttemptState attempt) {
        if (attempt.getStatus() != AttemptStatus.PENDING) {
            return;
        }
        LevelConfig level = resolveAttemptLevel(attempt);
        if (level == null) {
            return;
        }
        GenerationResult result;
        try {
            result = rewardGenerator.generate(level, attempt.getDeposit(), attempt.getSeed(), attempt.getBubbleCount());
        } catch (IllegalArgumentException ex) {
            return;
        }

        List<Integer> slots = config.bubbleSlots();
        int bubbleCount = Math.min(attempt.getBubbleCount(), slots.size());
        List<Integer> renderSlots = resolveBubbleRenderSlots(attempt, slots, bubbleCount);
        for (int i = 0; i < bubbleCount; i++) {
            int index = i;
            int slot = renderSlots.get(index);
            Bubble bubble = result.bubbles().get(index);
            if (attempt.isCollected(index)) {
                ItemStack collected = MenuItemBuilder.of(Material.GRAY_DYE)
                        .displayName("&7已收取")
                        .lore(List.of("&7这个气泡已经被收取了"))
                        .build();
                contents.set(slot, DisplayItem.of(collected));
                continue;
            }
            MenuIconDefinition icon = bubble.type() == com.cuzz.rookiefortunetree.model.BubbleType.CRIT
                    ? iconConfig.get("BUBBLE_CRIT")
                    : iconConfig.get("BUBBLE_NORMAL");
            ItemStack item = buildIcon(icon, Map.of("amount", ""), Material.GOLD_NUGGET, "&e元宝气泡");
            contents.set(slot, ClickableItem.of(item, e -> dispatcher.collectBubble(player, index)));
        }
    }

    private List<Integer> resolveBubbleRenderSlots(AttemptState attempt, List<Integer> configuredSlots, int bubbleCount) {
        if (configuredSlots == null || configuredSlots.isEmpty() || bubbleCount <= 0) {
            return List.of();
        }
        List<Integer> randomized = new ArrayList<>(configuredSlots);
        long seed = 0L;
        if (attempt != null) {
            seed = attempt.getSeed()
                    ^ attempt.getCreatedAtMillis()
                    ^ (((long) attempt.getRerollCount()) << 32)
                    ^ (long) attempt.getLevel();
        }
        Collections.shuffle(randomized, new Random(seed));
        if (randomized.size() <= bubbleCount) {
            return randomized;
        }
        return new ArrayList<>(randomized.subList(0, bubbleCount));
    }

    private LevelConfig resolveAttemptLevel(AttemptState attempt) {
        if (attempt == null) {
            return null;
        }
        LevelConfig base = config.levelById(attempt.getLevel());
        if (base == null) {
            return null;
        }
        int rewardMax = attempt.getRewardMax();
        if (rewardMax <= 0 || rewardMax == base.rewardMax()) {
            return base;
        }
        return new LevelConfig(
                base.level(),
                base.name(),
                base.deposit(),
                rewardMax,
                base.expToNext(),
                base.bubbleMin(),
                base.bubbleMax(),
                base.critChance(),
                base.critShareMin(),
                base.critShareMax()
        );
    }

    private int previewDeposit(PlayerState state, LevelConfig level) {
        if (state == null) {
            return 0;
        }
        if (!state.isFirstDone()) {
            return Math.max(0, config.newbieFirstPickCost());
        }
        if (state.getFreePicks() > 0) {
            return 0;
        }
        return level == null ? 0 : Math.max(0, level.deposit());
    }

    private String resolveStatus(AttemptState attempt, int remaining) {
        if (attempt.getStatus() == AttemptStatus.PENDING) {
            return "待收取";
        }
        if (remaining <= 0) {
            return "今日已完成";
        }
        return "可浇水";
    }

    private void sendRules(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage(TextUtil.colorize("&e[元宝树] &7玩法说明："));
        player.sendMessage(TextUtil.colorize("&7- 首次摘取需花费 &6" + config.newbieFirstPickCost()
                + " &7元宝，之后获得 &6" + config.newbieFreePicksAfterFirst() + " &7次免费机会"));
        player.sendMessage(TextUtil.colorize("&7- 每日仅可摘取 &6" + config.dailyLimit() + " &7次（可配置重置时间）"));
        if (config.rerollEnabled()) {
            player.sendMessage(TextUtil.colorize("&7- 未收取前退出再进入，可刷新结果（见好就收）"));
        }
    }

    private ItemStack buildIcon(MenuIconDefinition icon,
                                Map<String, String> placeholders,
                                Material fallbackMaterial,
                                String fallbackName) {
        if (icon == null) {
            return MenuItemBuilder.of(fallbackMaterial)
                    .displayName(fallbackName)
                    .build();
        }
        Material material = parseMaterial(icon.material(), fallbackMaterial);
        String name = TextUtil.format(icon.name(), placeholders);
        List<String> lore = icon.lore() == null ? List.of() : icon.lore().stream()
                .map(line -> TextUtil.format(line, placeholders))
                .toList();
        return MenuItemBuilder.of(material)
                .displayName(name.isBlank() ? fallbackName : name)
                .lore(lore)
                .build();
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
