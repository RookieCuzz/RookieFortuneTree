package com.cuzz.rookiefortunetree.service.economy;

import com.cuzz.bukkitspring.api.annotation.Autowired;
import com.cuzz.bukkitspring.api.annotation.Service;
import com.cuzz.rookiefortunetree.config.FortuneTreeConfig;
import com.cuzz.rookiefortunetree.model.EconomyType;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

@Service
public final class EconomyService implements EconomyGateway {
    private final FortuneTreeConfig config;
    private final PlayerPointsEconomyGateway playerPointsGateway;
    private final CommandEconomyGateway commandGateway;
    private final Logger logger;

    @Autowired
    public EconomyService(FortuneTreeConfig config,
                          PlayerPointsEconomyGateway playerPointsGateway,
                          CommandEconomyGateway commandGateway,
                          Logger logger) {
        this.config = config;
        this.playerPointsGateway = playerPointsGateway;
        this.commandGateway = commandGateway;
        this.logger = logger;
    }

    @Override
    public boolean take(Player player, int amount, String reason) {
        EconomyType type = config.economyType();
        if (type == EconomyType.PLAYERPOINTS) {
            return playerPointsGateway.take(player, amount, reason);
        }
        if (type == EconomyType.VAULT) {
            logger.warning("[FortuneTree] economy.type=vault is not implemented in this example, falling back to command mode.");
        }
        return commandGateway.take(player, amount, reason);
    }

    @Override
    public void give(Player player, int amount, String reason) {
        EconomyType type = config.economyType();
        if (type == EconomyType.PLAYERPOINTS) {
            if (!playerPointsGateway.give(player, amount, reason) && playerPointsGateway.isAvailable()) {
                logger.warning("[FortuneTree] PlayerPoints give operation returned false.");
            }
            return;
        }
        if (type == EconomyType.VAULT) {
            logger.warning("[FortuneTree] economy.type=vault is not implemented in this example, falling back to command mode.");
        }
        commandGateway.give(player, amount, reason);
    }
}
