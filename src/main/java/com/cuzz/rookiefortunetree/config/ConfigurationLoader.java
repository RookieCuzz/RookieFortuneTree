package com.cuzz.rookiefortunetree.config;

import org.bukkit.configuration.file.YamlConfiguration;

public interface ConfigurationLoader {
    YamlConfiguration load(String name);

    YamlConfiguration reload(String name);

    boolean exists(String name);
}

