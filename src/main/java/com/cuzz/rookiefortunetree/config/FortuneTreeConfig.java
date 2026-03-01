package com.cuzz.rookiefortunetree.config;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.bukkitspring.api.annotation.PostConstruct;
import com.cuzz.rookiefortunetree.model.EconomyType;
import com.cuzz.rookiefortunetree.model.LevelConfig;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class FortuneTreeConfig {
    private static final String CONFIG_FILE = "config.yml";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ConfigurationManager configurationManager;
    private volatile Data data;

    @Autowired
    public FortuneTreeConfig(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    public synchronized void reload() {
        YamlConfiguration config = configurationManager.reloadConfig(CONFIG_FILE);
        data = parse(config);
    }

    public int dailyLimit() {
        return data.dailyLimit;
    }

    public LocalTime resetAt() {
        return data.resetAt;
    }

    public int newbieFirstPickCost() {
        return data.newbieFirstPickCost;
    }

    public int newbieFreePicksAfterFirst() {
        return data.newbieFreeAfterFirst;
    }

    public boolean rerollEnabled() {
        return data.rerollEnabled;
    }

    public int rerollMaxTimesPerWater() {
        return data.rerollMaxTimesPerWater;
    }

    public boolean lockAfterFirstCollect() {
        return data.lockAfterFirstCollect;
    }

    public int profitMin() {
        return data.profitMin;
    }

    public EconomyType economyType() {
        return data.economyType;
    }

    public String takeDepositCommand() {
        return data.takeDepositCommand;
    }

    public String giveRewardCommand() {
        return data.giveRewardCommand;
    }

    public String menuTitle() {
        return data.menuTitle;
    }

    public Sound openSound() {
        return data.openSound;
    }

    public Sound waterSound() {
        return data.waterSound;
    }

    public Sound collectSound() {
        return data.collectSound;
    }

    public Sound failSound() {
        return data.failSound;
    }

    public List<Integer> bubbleSlots() {
        return data.bubbleSlots;
    }

    public int waterSlot() {
        return data.waterSlot;
    }

    public int collectAllSlot() {
        return data.collectAllSlot;
    }

    public int rulesSlot() {
        return data.rulesSlot;
    }

    public int closeSlot() {
        return data.closeSlot;
    }

    public String msgWaterDone() {
        return data.msgWaterDone;
    }

    public String msgCollectDone() {
        return data.msgCollectDone;
    }

    public String msgNoMoreTimes() {
        return data.msgNoMoreTimes;
    }

    public String msgAlreadyPending() {
        return data.msgAlreadyPending;
    }

    public String msgInvalidConfig() {
        return data.msgInvalidConfig;
    }

    public String msgRerollHint() {
        return data.msgRerollHint;
    }

    public List<LevelConfig> levels() {
        return data.levels;
    }

    public LevelConfig levelById(int levelId) {
        for (LevelConfig level : data.levels) {
            if (level.level() == levelId) {
                return level;
            }
        }
        return data.levels.isEmpty() ? null : data.levels.get(0);
    }

    private Data parse(YamlConfiguration config) {
        int dailyLimit = clampInt(config.getInt("daily.limit", 1), 1, 999);
        LocalTime resetAt = parseResetAt(config.getString("daily.resetAt", "00:00"));

        int newbieFirstPickCost = clampInt(config.getInt("newbie.firstPickCost", 100), 0, Integer.MAX_VALUE);
        int newbieFreeAfterFirst = clampInt(config.getInt("newbie.freePicksAfterFirst", 9), 0, 9999);

        boolean rerollEnabled = config.getBoolean("reroll.enabled", true);
        int rerollMaxTimesPerWater = clampInt(config.getInt("reroll.maxTimesPerWater", 10), 0, 9999);
        boolean lockAfterFirstCollect = config.getBoolean("reroll.lockAfterFirstCollect", true);

        int bubbleMinDefault = clampInt(config.getInt("bubbles.min", 8), 1, 54);
        int bubbleMaxDefault = clampInt(config.getInt("bubbles.max", 12), 1, 54);
        if (bubbleMaxDefault < bubbleMinDefault) {
            bubbleMaxDefault = bubbleMinDefault;
        }

        int profitMin = clampInt(config.getInt("profit.min", 1), 1, Integer.MAX_VALUE);

        double critChanceDefault = clampDouble(config.getDouble("crit.chance", 0.08), 0.0, 1.0);
        double critShareMinDefault = clampDouble(config.getDouble("crit.shareMin", 0.55), 0.0, 1.0);
        double critShareMaxDefault = clampDouble(config.getDouble("crit.shareMax", 0.80), 0.0, 1.0);
        if (critShareMaxDefault < critShareMinDefault) {
            critShareMaxDefault = critShareMinDefault;
        }

        List<LevelConfig> levels = parseLevels(config, bubbleMinDefault, bubbleMaxDefault,
                critChanceDefault, critShareMinDefault, critShareMaxDefault);

        EconomyType economyType = EconomyType.fromString(config.getString("economy.type", "playerpoints"));
        String takeDepositCommand = config.getString("economy.takeDepositCommand", "eco take {player} {deposit}");
        String giveRewardCommand = config.getString("economy.giveRewardCommand", "eco give {player} {amount}");

        String menuTitle = config.getString("menu.title", "&6元宝树");
        Sound openSound = parseSound(config.getString("menu.open-sound"), Sound.UI_BUTTON_CLICK);
        Sound waterSound = parseSound(config.getString("menu.water-sound"), Sound.ITEM_BUCKET_EMPTY);
        Sound collectSound = parseSound(config.getString("menu.collect-sound"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        Sound failSound = parseSound(config.getString("menu.fail-sound"), Sound.ENTITY_VILLAGER_NO);

        List<Integer> bubbleSlots = parseIntList(config.getList("menu.bubble-slots"), defaultBubbleSlots());
        int waterSlot = clampInt(config.getInt("menu.water-slot", 49), 0, 53);
        int collectAllSlot = clampInt(config.getInt("menu.collect-all-slot", 51), 0, 53);
        int rulesSlot = clampInt(config.getInt("menu.rules-slot", 45), 0, 53);
        int closeSlot = clampInt(config.getInt("menu.close-slot", 53), 0, 53);

        String msgWaterDone = config.getString("messages.waterDone", "&a浇水成功！");
        String msgCollectDone = config.getString("messages.collectDone", "&a本次收获 &6{amount}&a 元宝");
        String msgNoMoreTimes = config.getString("messages.noMoreTimes", "&c今日次数已用尽。");
        String msgAlreadyPending = config.getString("messages.alreadyPending", "&e你还有未收取的气泡。");
        String msgInvalidConfig = config.getString("messages.invalidConfig", "&c配置错误：{reason}");
        String msgRerollHint = config.getString("messages.rerollHint", "&7未收取前退出再进入，可刷新结果（见好就收）");

        return new Data(dailyLimit, resetAt, newbieFirstPickCost, newbieFreeAfterFirst,
                rerollEnabled, rerollMaxTimesPerWater, lockAfterFirstCollect,
                bubbleMinDefault, bubbleMaxDefault, profitMin,
                critChanceDefault, critShareMinDefault, critShareMaxDefault,
                Collections.unmodifiableList(levels),
                economyType, safeString(takeDepositCommand), safeString(giveRewardCommand),
                safeString(menuTitle), openSound, waterSound, collectSound, failSound,
                Collections.unmodifiableList(bubbleSlots),
                waterSlot, collectAllSlot, rulesSlot, closeSlot,
                safeString(msgWaterDone), safeString(msgCollectDone),
                safeString(msgNoMoreTimes), safeString(msgAlreadyPending),
                safeString(msgInvalidConfig), safeString(msgRerollHint));
    }

    private List<LevelConfig> parseLevels(YamlConfiguration config,
                                         int bubbleMinDefault,
                                         int bubbleMaxDefault,
                                         double critChanceDefault,
                                         double critShareMinDefault,
                                         double critShareMaxDefault) {
        List<Map<?, ?>> raw = config.getMapList("levels");
        if (raw == null || raw.isEmpty()) {
            return List.of(new LevelConfig(1, "LV1", 0, 240, 0,
                    bubbleMinDefault, bubbleMaxDefault,
                    critChanceDefault, critShareMinDefault, critShareMaxDefault));
        }
        List<LevelConfig> results = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            int level = getInt(entry.get("level"), 1);
            String name = getString(entry.get("name"), "LV" + level);
            int deposit = clampInt(getInt(entry.get("deposit"), 0), 0, Integer.MAX_VALUE);
            int rewardMax = clampInt(getInt(entry.get("rewardMax"), 240), 0, Integer.MAX_VALUE);
            int expToNext = clampInt(getInt(entry.get("expToNext"), 0), 0, Integer.MAX_VALUE);

            int bubbleMin = clampInt(getInt(entry.get("bubbleMin"), bubbleMinDefault), 1, 54);
            int bubbleMax = clampInt(getInt(entry.get("bubbleMax"), bubbleMaxDefault), 1, 54);
            if (bubbleMax < bubbleMin) {
                bubbleMax = bubbleMin;
            }

            double critChance = clampDouble(getDouble(entry.get("critChance"), critChanceDefault), 0.0, 1.0);
            double critShareMin = clampDouble(getDouble(entry.get("critShareMin"), critShareMinDefault), 0.0, 1.0);
            double critShareMax = clampDouble(getDouble(entry.get("critShareMax"), critShareMaxDefault), 0.0, 1.0);
            if (critShareMax < critShareMin) {
                critShareMax = critShareMin;
            }

            results.add(new LevelConfig(level, name, deposit, rewardMax, expToNext,
                    bubbleMin, bubbleMax, critChance, critShareMin, critShareMax));
        }
        results.sort((a, b) -> Integer.compare(a.level(), b.level()));
        return results;
    }

    private LocalTime parseResetAt(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (Exception ignored) {
            return LocalTime.MIDNIGHT;
        }
    }

    private Sound parseSound(String value, Sound fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<Integer> parseIntList(Object value, List<Integer> fallback) {
        if (value instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) {
                    result.add(number.intValue());
                } else if (item instanceof String text) {
                    try {
                        result.add(Integer.parseInt(text.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return fallback == null ? List.of() : fallback;
    }

    private List<Integer> defaultBubbleSlots() {
        return List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 32, 33, 34, 37, 38, 39, 41, 42, 43);
    }

    private int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private double clampDouble(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private int getInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double getDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String getString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private record Data(
            int dailyLimit,
            LocalTime resetAt,
            int newbieFirstPickCost,
            int newbieFreeAfterFirst,
            boolean rerollEnabled,
            int rerollMaxTimesPerWater,
            boolean lockAfterFirstCollect,
            int bubbleMinDefault,
            int bubbleMaxDefault,
            int profitMin,
            double critChanceDefault,
            double critShareMinDefault,
            double critShareMaxDefault,
            List<LevelConfig> levels,
            EconomyType economyType,
            String takeDepositCommand,
            String giveRewardCommand,
            String menuTitle,
            Sound openSound,
            Sound waterSound,
            Sound collectSound,
            Sound failSound,
            List<Integer> bubbleSlots,
            int waterSlot,
            int collectAllSlot,
            int rulesSlot,
            int closeSlot,
            String msgWaterDone,
            String msgCollectDone,
            String msgNoMoreTimes,
            String msgAlreadyPending,
            String msgInvalidConfig,
            String msgRerollHint
    ) {
    }
}
