package com.cuzz.rookiefortunetree.controller;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Controller;
import com.cuzz.rookiefortunetree.event.FortuneTreeRequestEvent;
import com.cuzz.rookiefortunetree.service.FortuneTreeService;
import com.cuzz.rookiefortunetree.storage.FortuneTreeStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Controller
public final class FortuneTreeController implements Listener {
    private final FortuneTreeService service;
    private final FortuneTreeStore store;

    @Autowired
    public FortuneTreeController(FortuneTreeService service, FortuneTreeStore store) {
        this.service = service;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRequest(FortuneTreeRequestEvent event) {
        switch (event.getAction()) {
            case OPEN_GUI -> service.openMenu(event.getPlayer(), true);
            case WATER -> service.water(event.getPlayer(), event.getLevelOverride());
            case COLLECT_BUBBLE -> service.collectBubble(event.getPlayer(), event.getBubbleIndex());
            case COLLECT_ALL -> service.collectAll(event.getPlayer());
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        store.evictPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        store.evictPlayer(event.getPlayer().getUniqueId());
    }
}
