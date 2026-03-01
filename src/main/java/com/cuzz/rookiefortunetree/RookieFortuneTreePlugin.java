package com.cuzz.rookiefortunetree;

import com.cuzz.bukkitspring.BukkitSpring;
import com.cuzz.bukkitspring.api.ApplicationContext;
import com.cuzz.bukkitspring.platform.bukkit.BukkitPlatformContext;
import org.bukkit.plugin.java.JavaPlugin;

public final class RookieFortuneTreePlugin extends JavaPlugin {
    private ApplicationContext context;

    @Override
    public void onLoad() {
        context = BukkitSpring.registerPlugin(this, new BukkitPlatformContext(this), "com.cuzz.rookiefortunetree");
    }

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveResource("menu_icons.yml", false);
        context.refresh();
    }

    @Override
    public void onDisable() {
        BukkitSpring.unregisterPlugin(this);
    }
}

