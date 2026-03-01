package com.cuzz.rookiefortunetree.service.economy;

import org.bukkit.entity.Player;

public interface EconomyGateway {
    boolean take(Player player, int amount, String reason);

    void give(Player player, int amount, String reason);
}

