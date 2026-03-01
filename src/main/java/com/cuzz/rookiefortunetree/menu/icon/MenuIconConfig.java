package com.cuzz.rookiefortunetree.menu.icon;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.bukkitspring.api.annotation.PostConstruct;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public final class MenuIconConfig {
    private static final String CONFIG_FILE = "menu_icons.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile Map<String, MenuIconDefinition> byId = Map.of();

    @Autowired
    public MenuIconConfig(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    public synchronized void reload() {
        Object root = loadRaw();
        byId = Collections.unmodifiableMap(parse(root));
    }

    public MenuIconDefinition get(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return byId.get(id.trim().toUpperCase(Locale.ROOT));
    }

    private Object loadRaw() {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.saveResource(CONFIG_FILE, false);
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return new Yaml().load(text);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to read " + CONFIG_FILE, ex);
            return null;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[FortuneTree] Failed to parse " + CONFIG_FILE, ex);
            return null;
        }
    }

    private Map<String, MenuIconDefinition> parse(Object root) {
        Map<String, MenuIconDefinition> result = new HashMap<>();
        List<?> items;
        if (root instanceof List<?> list) {
            items = list;
        } else {
            items = List.of();
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            MenuIconDefinition def = parseOne(map);
            if (def == null || def.iconId() == null || def.iconId().isBlank()) {
                continue;
            }
            result.put(def.iconId().trim().toUpperCase(Locale.ROOT), def);
        }
        return result;
    }

    private MenuIconDefinition parseOne(Map<?, ?> map) {
        String iconId = getString(map.get("IconID"));
        String material = getString(map.get("Material"));
        String name = getString(map.get("Name"));
        List<String> lore = getStringList(map.get("Lore"));
        return new MenuIconDefinition(iconId, material, name, lore);
    }

    private List<String> getStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of();
    }

    private String getString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

