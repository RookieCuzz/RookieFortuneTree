package com.cuzz.rookiefortunetree.config;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@Component
public final class FileConfigurationLoader implements ConfigurationLoader {
    private final JavaPlugin plugin;

    @Autowired
    public FileConfigurationLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public YamlConfiguration load(String name) {
        File file = resolve(name);
        ensureExists(file, name);
        return YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public YamlConfiguration reload(String name) {
        return load(name);
    }

    @Override
    public boolean exists(String name) {
        return resolve(name).exists();
    }

    private File resolve(String name) {
        return new File(plugin.getDataFolder(), name);
    }

    private void ensureExists(File file, String name) {
        if (file.exists()) {
            return;
        }
        plugin.getDataFolder().mkdirs();
        plugin.saveResource(name, false);
    }
}

