package com.cuzz.rookiefortunetree.wrapper;

import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.rookiefortunetree.event.FortuneTreeRequestEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Component
public final class FortuneTreeActionDispatcher {
    public void openGui(Player player) {
        dispatch(player, new FortuneTreeRequestEvent(player, FortuneTreeRequestEvent.Action.OPEN_GUI));
    }

    public void water(Player player, Integer levelOverride) {
        dispatch(player, new FortuneTreeRequestEvent(player, FortuneTreeRequestEvent.Action.WATER, -1, levelOverride));
    }

    public void collectBubble(Player player, int index) {
        dispatch(player, new FortuneTreeRequestEvent(player, FortuneTreeRequestEvent.Action.COLLECT_BUBBLE, index, null));
    }

    public void collectAll(Player player) {
        dispatch(player, new FortuneTreeRequestEvent(player, FortuneTreeRequestEvent.Action.COLLECT_ALL));
    }

    private void dispatch(Player player, FortuneTreeRequestEvent event) {
        if (player == null || event == null) {
            return;
        }
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() && event.getCancelReason() != null && !event.getCancelReason().isBlank()) {
            player.sendMessage(event.getCancelReason());
        }
    }
}

