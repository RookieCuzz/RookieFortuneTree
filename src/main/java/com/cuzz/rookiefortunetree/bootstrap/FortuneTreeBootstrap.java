package com.cuzz.rookiefortunetree.bootstrap;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.bukkitspring.api.annotation.PostConstruct;
import com.cuzz.rookiefortunetree.command.FortuneTreeCommandWrapper;
import com.cuzz.rookiefortunetree.controller.FortuneTreeController;
import com.cuzz.rookiefortunetree.storage.FortuneTreeStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

@Component
public final class FortuneTreeBootstrap {
    private final JavaPlugin plugin;
    private final FortuneTreeCommandWrapper commandWrapper;
    private final FortuneTreeController controller;
    private final FortuneTreeSchemaInitializer schemaInitializer;
    private final FortuneTreeStore store;

    @Autowired
    public FortuneTreeBootstrap(JavaPlugin plugin,
                               FortuneTreeCommandWrapper commandWrapper,
                               FortuneTreeController controller,
                               FortuneTreeSchemaInitializer schemaInitializer,
                               FortuneTreeStore store) {
        this.plugin = plugin;
        this.commandWrapper = commandWrapper;
        this.controller = controller;
        this.schemaInitializer = schemaInitializer;
        this.store = store;
    }

    @PostConstruct
    public void register() {
        schemaInitializer.initialize();
        plugin.getLogger().info("[FortuneTree] Store=" + store.getClass().getSimpleName());
        PluginCommand wt = plugin.getCommand("wt");
        if (wt != null) {
            wt.setExecutor(commandWrapper);
            wt.setTabCompleter(commandWrapper);
        }
        PluginCommand water = plugin.getCommand("water");
        if (water != null) {
            water.setExecutor(commandWrapper);
            water.setTabCompleter(commandWrapper);
        }
        Bukkit.getPluginManager().registerEvents(controller, plugin);
    }
}
