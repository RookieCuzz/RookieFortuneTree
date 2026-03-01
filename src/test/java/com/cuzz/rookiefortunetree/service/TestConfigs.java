package com.cuzz.rookiefortunetree.service;

import com.cuzz.rookiefortunetree.config.ConfigurationLoader;
import com.cuzz.rookiefortunetree.config.ConfigurationManager;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.logging.Logger;
import java.util.function.Consumer;

final class TestConfigs {
    private TestConfigs() {
    }

    static FortuneTreeConfig fortuneTreeConfig(Consumer<YamlConfiguration> configurer) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (configurer != null) {
            configurer.accept(yaml);
        }
        ConfigurationLoader loader = new ConfigurationLoader() {
            @Override
            public YamlConfiguration load(String name) {
                return yaml;
            }

            @Override
            public YamlConfiguration reload(String name) {
                return yaml;
            }

            @Override
            public boolean exists(String name) {
                return true;
            }
        };
        FortuneTreeConfig config = new FortuneTreeConfig(
                new ConfigurationManager(loader),
                Logger.getLogger("Test"),
                null
        );
        config.reload();
        return config;
    }
}
