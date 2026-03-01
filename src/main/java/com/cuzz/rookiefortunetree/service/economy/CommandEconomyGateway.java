package com.cuzz.rookiefortunetree.service.economy;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Component;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

@Component
public final class CommandEconomyGateway {
    private final FortuneTreeConfig config;

    @Autowired
    public CommandEconomyGateway(FortuneTreeConfig config) {
        this.config = config;
    }

    public boolean take(Player player, int amount, String reason) {
        if (player == null) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        String command = TextUtil.format(config.takeDepositCommand(), Map.of(
                "player", player.getName(),
                "deposit", String.valueOf(amount),
                "amount", String.valueOf(amount),
                "reason", reason == null ? "" : reason
        ));
        return dispatch(command);
    }

    public void give(Player player, int amount, String reason) {
        if (player == null || amount <= 0) {
            return;
        }
        String command = TextUtil.format(config.giveRewardCommand(), Map.of(
                "player", player.getName(),
                "deposit", "0",
                "amount", String.valueOf(amount),
                "reason", reason == null ? "" : reason
        ));
        dispatch(command);
    }

    private boolean dispatch(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        CommandSender console = Bukkit.getConsoleSender();
        try {
            return Bukkit.dispatchCommand(console, command);
        } catch (Exception ignored) {
            return false;
        }
    }
}
