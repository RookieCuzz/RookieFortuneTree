package com.cuzz.rookiefortunetree.config;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class ConfigurationManager {
    private final ConfigurationLoader loader;
    private final Map<String, YamlConfiguration> cache = new ConcurrentHashMap<>();

    @Autowired
    public ConfigurationManager(ConfigurationLoader loader) {
        this.loader = loader;
    }

    public YamlConfiguration getConfig(String name) {
        return cache.computeIfAbsent(name, loader::load);
    }

    public YamlConfiguration reloadConfig(String name) {
        YamlConfiguration config = loader.reload(name);
        cache.put(name, config);
        return config;
    }
}

